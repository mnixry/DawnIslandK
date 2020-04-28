package com.laotoua.dawnislandk.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.laotoua.dawnislandk.R
import com.laotoua.dawnislandk.data.entity.Thread
import com.laotoua.dawnislandk.data.network.ImageLoader
import com.laotoua.dawnislandk.databinding.FeedFragmentBinding
import com.laotoua.dawnislandk.ui.adapter.QuickAdapter
import com.laotoua.dawnislandk.ui.popup.ImageViewerPopup
import com.laotoua.dawnislandk.viewmodel.FeedViewModel
import com.laotoua.dawnislandk.viewmodel.LoadingStatus
import com.laotoua.dawnislandk.viewmodel.SharedViewModel
import com.lxj.xpopup.XPopup
import me.dkzwm.widget.srl.RefreshingListenerAdapter
import me.dkzwm.widget.srl.extra.header.ClassicHeader
import me.dkzwm.widget.srl.indicator.IIndicator
import timber.log.Timber


class FeedFragment : Fragment() {

    private var _binding: FeedFragmentBinding? = null
    private val binding: FeedFragmentBinding get() = _binding!!

    private val viewModel: FeedViewModel by viewModels()
    private val sharedVM: SharedViewModel by activityViewModels()
    private val mAdapter = QuickAdapter(R.layout.thread_list_item)

    private val imageLoader: ImageLoader by lazy { ImageLoader(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        sharedVM.setFragment(this)
        _binding = FeedFragmentBinding.inflate(inflater, container, false)
        binding.feedsView.layoutManager = LinearLayoutManager(context)
        binding.feedsView.adapter = mAdapter

        /*** connect SharedVm and adapter
         *  may have better way of getting runtime data
         */
        mAdapter.setSharedVM(sharedVM)

        binding.refreshLayout.setHeaderView(ClassicHeader<IIndicator>(context))
        binding.refreshLayout.setOnRefreshListener(object : RefreshingListenerAdapter() {
            override fun onRefreshing() {
                mAdapter.setNewInstance(mutableListOf())
                viewModel.refresh()
            }
        })

        // item click
        mAdapter.setOnItemClickListener { adapter, _, position ->
            sharedVM.setThread(adapter.getItem(position) as Thread)
            val action =
                PagerFragmentDirections.actionPagerFragmentToReplyFragment()
            findNavController().navigate(action)
            Timber.i("clicked on item")

        }

        // long click to delete
        mAdapter.setOnItemLongClickListener { _, _, position ->
            val id = (mAdapter.getItem(position) as Thread).id
            MaterialDialog(requireContext()).show {
                title(text = "删除订阅 $id?")
                positiveButton(text = "删除") {
                    viewModel.deleteFeed(id, position)
                }
                negativeButton(text = "取消")
            }

            true
        }

        viewModel.delFeedResponse.observe(viewLifecycleOwner, Observer { it ->
            it.getContentIfNotHandled()?.let { eventPayload ->
                Toast.makeText(context, eventPayload.message, Toast.LENGTH_SHORT).show()
                if (eventPayload.loadingStatus == LoadingStatus.SUCCESS) mAdapter.remove(
                    eventPayload.payload!!
                )
            }
        })
        mAdapter.addChildClickViewIds(R.id.threadImage)
        mAdapter.setOnItemChildClickListener { adapter, view, position ->
            if (view.id == R.id.threadImage) {

                Timber.i("clicked on image at $position")
                val url = (adapter.getItem(
                    position
                ) as Thread).getImgUrl()

                // TODO support multiple image
                val viewerPopup =
                    ImageViewerPopup(
                        this,
                        requireContext(),
                        url
                    )
                viewerPopup.setXPopupImageLoader(imageLoader)
                viewerPopup.setSingleSrcView(view as ImageView?, url)

                XPopup.Builder(context)
                    .asCustom(viewerPopup)
                    .show()
            }
        }


        // load more
        mAdapter.loadMoreModule.setOnLoadMoreListener {
            Timber.i("Fetching new data...")
            viewModel.getFeeds()
        }

        viewModel.loadingStatus.observe(viewLifecycleOwner, Observer {
            it.getContentIfNotHandled()?.run {
                when (this.loadingStatus) {
                    LoadingStatus.FAILED -> {
                        if (binding.refreshLayout.isRefreshing) {
                            binding.refreshLayout.refreshComplete(false)
                        } else {
                            mAdapter.loadMoreModule.loadMoreFail()
                        }
                        Toast.makeText(
                            context,
                            "${it.peekContent().message}",
                            Toast.LENGTH_LONG
                        ).show()
                        Timber.e(message)
                    }
                    LoadingStatus.NODATA -> {
                        if (binding.refreshLayout.isRefreshing) {
                            binding.refreshLayout.refreshComplete(true)
                        } else {
                            mAdapter.loadMoreModule.loadMoreEnd()
                        }
                        Timber.i("Finished loading data...")
                    }
                    else -> {
                        // do nothing
                        Timber.i(this.loadingStatus.name)
                    }

                }
            }
        })

        viewModel.feeds.observe(viewLifecycleOwner, Observer {
            mAdapter.setDiffNewData(it.toMutableList())
            mAdapter.loadMoreModule.loadMoreComplete()
            binding.refreshLayout.refreshComplete()
            Timber.i("New data found. Adapter now have ${mAdapter.data.size} threads")

        })

        return binding.root
    }
}