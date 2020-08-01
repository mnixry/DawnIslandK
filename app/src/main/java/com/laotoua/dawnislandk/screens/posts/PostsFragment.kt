/*
 *  Copyright 2020 Fishballzzz
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.laotoua.dawnislandk.screens.posts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.laotoua.dawnislandk.DawnApp
import com.laotoua.dawnislandk.MainNavDirections
import com.laotoua.dawnislandk.R
import com.laotoua.dawnislandk.data.local.entity.Post
import com.laotoua.dawnislandk.databinding.FragmentPostBinding
import com.laotoua.dawnislandk.screens.MainActivity
import com.laotoua.dawnislandk.screens.adapters.QuickAdapter
import com.laotoua.dawnislandk.screens.util.Layout.toast
import com.laotoua.dawnislandk.screens.util.Layout.updateHeaderAndFooter
import com.laotoua.dawnislandk.screens.widgets.BaseNavFragment
import com.laotoua.dawnislandk.screens.widgets.popups.ImageViewerPopup
import com.laotoua.dawnislandk.screens.widgets.popups.PostPopup
import com.laotoua.dawnislandk.util.DawnConstants
import com.laotoua.dawnislandk.util.EventPayload
import com.laotoua.dawnislandk.util.SingleLiveEvent
import com.laotoua.dawnislandk.util.lazyOnMainOnly
import com.lxj.xpopup.XPopup
import me.dkzwm.widget.srl.RefreshingListenerAdapter
import me.dkzwm.widget.srl.config.Constants
import timber.log.Timber


class PostsFragment : BaseNavFragment() {

    private var binding: FragmentPostBinding? = null
    private var mAdapter: QuickAdapter<Post>? = null
    private val viewModel: PostsViewModel by viewModels { viewModelFactory }
    private val postPopup: PostPopup by lazyOnMainOnly { PostPopup(requireActivity(), sharedVM) }
    private var isFabOpen = false

    private val postObs = Observer<List<Post>> {
        if (mAdapter == null) return@Observer
        if (it.isEmpty()) {
            if (!mAdapter!!.hasEmptyView()) mAdapter!!.setDefaultEmptyView()
            mAdapter!!.setDiffNewData(null)
            return@Observer
        }
        // set forum when navigate from website url
        if (sharedVM.selectedForumId.value == null) {
            sharedVM.setForumId(it.first().fid)
        }
        mAdapter!!.setDiffNewData(it.toMutableList())
        Timber.i("${this.javaClass.simpleName} Adapter will have ${it.size} threads")
    }

    private val forumIdObs = Observer<String> {
        if (mAdapter == null) return@Observer
        if (viewModel.currentFid != it) mAdapter!!.setList(emptyList())
        viewModel.setForum(it)
    }

    private val loadingObs = Observer<SingleLiveEvent<EventPayload<Nothing>>> {
        if (mAdapter == null || binding == null) return@Observer
        it.getContentIfNotHandled()?.run {
            updateHeaderAndFooter(binding!!.srlAndRv.refreshLayout, mAdapter!!, this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_fragment_post, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.forumRule -> {
                val fid = sharedVM.selectedForumId.value
                if (fid == null) {
                    toast(R.string.please_try_again_later)
                    return true
                }
                val fidInt: Int?
                try {
                    fidInt = fid.toInt()
                } catch (e: Exception) {
                    toast(R.string.did_not_select_forum_id)
                    return true
                }
                MaterialDialog(requireContext()).show {
                    val biId = if (fidInt > 0) fidInt else 1
                    val resourceId: Int = context.resources.getIdentifier(
                        "bi_$biId", "drawable",
                        context.packageName
                    )
                    icon(resourceId)
                    title(text = sharedVM.getForumDisplayName(fid))
                    message(text = sharedVM.getForumMsg(fid)) {
                        html { link ->
                            val uri = if (link.startsWith("/")) {
                                DawnConstants.nmbHost + link
                            } else link
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                                startActivity(intent)
                            }
                        }
                    }
                    positiveButton(R.string.acknowledge)
                }
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (mAdapter == null) {
            mAdapter = QuickAdapter<Post>(R.layout.list_item_post, sharedVM).apply {
                setOnItemClickListener { _, _, position ->
                    getItem(position).run {
                        val navAction =
                            MainNavDirections.actionGlobalCommentsFragment(id, fid)
                        findNavController().navigate(navAction)
                    }
                }
                setOnItemLongClickListener { _, _, position ->
                    MaterialDialog(requireContext()).show {
                        title(R.string.post_options)
                        listItems(R.array.post_options) { _, index, _ ->
                            when (index) {
                                0 -> {
                                    MaterialDialog(requireContext()).show {
                                        title(R.string.report_reasons)
                                        listItemsSingleChoice(res = R.array.report_reasons) { _, _, text ->
                                            postPopup.setupAndShow(
                                                "18",//值班室
                                                "18",
                                                newPost = true,
                                                quote = ">>No.${getItem(position).id}\n${context.getString(
                                                    R.string.report_reasons
                                                )}: $text\n"
                                            )
                                        }
                                        cancelOnTouchOutside(false)
                                    }
                                }
                                1 -> {
                                    val post = getItem(position)
                                    if (!post.isStickyTopBanner()) {
                                        viewModel.blockPost(post)
                                        toast(getString(R.string.blocked_post, post.id))
                                        mAdapter?.removeAt(position)
                                    } else {
                                        toast("你真的想屏蔽这个串吗？(ᯣ ̶̵̵̵̶̶̶̶̵̫̋̋̅̅̅ᯣ )", Toast.LENGTH_LONG)
                                    }
                                }
                                else -> {
                                    throw Exception("Unhandled option")
                                }
                            }
                        }
                    }
                    true
                }

                addChildClickViewIds(R.id.attachedImage)
                setOnItemChildClickListener { _, view, position ->
                    if (view.id == R.id.attachedImage) {
                        val viewerPopup = ImageViewerPopup(requireContext())
                        viewerPopup.setSingleSrcView(view as ImageView?, getItem(position))
                        XPopup.Builder(context)
                            .asCustom(viewerPopup)
                            .show()
                    }
                }

                loadMoreModule.setOnLoadMoreListener {
                    viewModel.getPosts()
                }
            }
        }

        if (binding != null) {
            Timber.d("Fragment View Reusing!")
        } else {
            Timber.d("Fragment View Created")
            binding = FragmentPostBinding.inflate(inflater, container, false)

            binding!!.srlAndRv.refreshLayout.apply {
                setOnRefreshListener(object : RefreshingListenerAdapter() {
                    override fun onRefreshing() {
                        viewModel.refresh()
                    }
                })
            }

            binding!!.srlAndRv.recyclerView.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = mAdapter
                setHasFixedSize(true)
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        if (dy > 0) {
                            hideFabMenu()
                            binding?.fabMenu?.hide()
                            binding?.fabMenu?.isClickable = false
                        } else if (dy < 0) {
                            binding?.fabMenu?.show()
                            binding?.fabMenu?.isClickable = true
                        }
                    }
                })
            }

            binding!!.fabMenu.setOnClickListener {
                toggleFabMenu()
            }

            binding!!.post.setOnClickListener {
                if (sharedVM.selectedForumId.value == null) {
                    toast(R.string.please_try_again_later)
                    return@setOnClickListener
                }
                hideFabMenu()
                postPopup.setupAndShow(
                    sharedVM.selectedForumId.value,
                    sharedVM.selectedForumId.value!!,
                    true
                )
            }

            binding!!.announcement.setOnClickListener {
                hideFabMenu()
                DawnApp.applicationDataStore.nmbNotice?.let { notice ->
                    MaterialDialog(requireContext()).show {
                        title(res = R.string.announcement)
                        message(text = notice.content) { html() }
                        positiveButton(R.string.close)
                    }
                }
            }

            binding!!.flingInterceptor.bindListener {
                (activity as MainActivity).showDrawer()
            }
        }
        return binding!!.root
    }

    override fun onResume() {
        super.onResume()
        // initial load
        if (viewModel.posts.value.isNullOrEmpty()) {
            binding?.srlAndRv?.refreshLayout?.autoRefresh(
                Constants.ACTION_NOTHING,
                false
            )
        }

        viewModel.loadingStatus.observe(viewLifecycleOwner, loadingObs)
        viewModel.posts.observe(viewLifecycleOwner, postObs)
        sharedVM.selectedForumId.observe(viewLifecycleOwner, forumIdObs)
    }

    override fun onPause() {
        super.onPause()
        viewModel.loadingStatus.removeObserver(loadingObs)
        viewModel.posts.removeObserver(postObs)
        sharedVM.selectedForumId.removeObserver(forumIdObs)
    }

    private fun hideFabMenu() {
        val rotateBackward = AnimationUtils.loadAnimation(
            requireContext(),
            R.anim.rotate_backward
        )
        binding?.fabMenu?.startAnimation(rotateBackward)
        binding?.announcement?.hide()
        binding?.post?.hide()
        isFabOpen = false
    }

    private fun showFabMenu() {
        val rotateForward = AnimationUtils.loadAnimation(
            requireContext(),
            R.anim.rotate_forward
        )
        binding?.fabMenu?.startAnimation(rotateForward)
        binding?.announcement?.show()
        binding?.post?.show()
        isFabOpen = true
    }

    private fun toggleFabMenu() {
        if (isFabOpen) {
            hideFabMenu()
        } else {
            showFabMenu()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (!DawnApp.applicationDataStore.getViewCaching()) {
            mAdapter = null
            binding = null
        }
        Timber.d("Fragment View Destroyed ${binding == null}")
    }
}

