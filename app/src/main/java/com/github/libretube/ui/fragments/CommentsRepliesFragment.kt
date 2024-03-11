package com.github.libretube.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.libretube.R
import com.github.libretube.api.obj.Comment
import com.github.libretube.constants.IntentData
import com.github.libretube.databinding.FragmentCommentsBinding
import com.github.libretube.extensions.formatShort
import com.github.libretube.extensions.parcelable
import com.github.libretube.ui.adapters.CommentPagingAdapter
import com.github.libretube.ui.models.CommentsViewModel
import com.github.libretube.ui.sheets.CommentsSheet
import kotlinx.coroutines.launch

class CommentsRepliesFragment : Fragment() {
    private var _binding: FragmentCommentsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CommentsViewModel by activityViewModels()

    private var scrollListener: ViewTreeObserver.OnScrollChangedListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCommentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val arguments = requireArguments()
        val videoId = arguments.getString(IntentData.videoId, "")
        val comment = arguments.parcelable<Comment>(IntentData.comment)!!

        val binding = binding

        val commentsSheet = parentFragment as? CommentsSheet
        commentsSheet?.binding?.btnScrollToTop?.isGone = true

        val repliesAdapter = CommentPagingAdapter(
            null,
            videoId,
            viewModel.channelAvatar,
            comment,
            viewModel.handleLink
        ) {
            viewModel.commentsSheetDismiss?.invoke()
        }
        (parentFragment as CommentsSheet).updateFragmentInfo(
            true,
            "${getString(R.string.replies)} (${comment.replyCount.formatShort()})"
        )

        binding.commentsRV.updatePadding(top = 0)

        val layoutManager = LinearLayoutManager(context)
        binding.commentsRV.layoutManager = layoutManager

        binding.commentsRV.adapter = repliesAdapter

        //init scroll position
        if (viewModel.currentRepliesPosition.value != null) {
            if (viewModel.currentRepliesPosition.value!! > POSITION_START) {
                layoutManager.scrollToPosition(viewModel.currentRepliesPosition.value!!)
            } else {
                layoutManager.scrollToPosition(POSITION_START)
            }
        }

        commentsSheet?.binding?.btnScrollToTop?.setOnClickListener {
            // scroll back to the top / first comment
            layoutManager.scrollToPosition(POSITION_START)
            viewModel.changeReplayPosition(POSITION_START)
        }

        scrollListener = ViewTreeObserver.OnScrollChangedListener {
            // save the last scroll position to become used next time when the sheet is opened
            viewModel.changeReplayPosition(layoutManager.findFirstVisibleItemPosition())
            // hide or show the scroll to top button
            commentsSheet?.binding?.btnScrollToTop?.isVisible =
                viewModel.currentRepliesPosition.value != 0
        }

        binding.commentsRV.viewTreeObserver.addOnScrollChangedListener(scrollListener)

        viewModel.selectedCommentLiveData.postValue(comment.repliesPage)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    repliesAdapter.loadStateFlow.collect {
                        binding.progress.isVisible = it.refresh is LoadState.Loading
                    }
                }

                launch {
                    viewModel.commentRepliesFlow.collect {
                        repliesAdapter.submitData(it)
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        binding.commentsRV.viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        scrollListener = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val POSITION_START = 0
    }
}
