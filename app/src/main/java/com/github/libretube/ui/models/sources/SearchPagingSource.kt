package com.github.libretube.ui.models.sources

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.libretube.api.RetrofitInstance
import com.github.libretube.api.obj.ContentItem
import com.github.libretube.util.deArrow
import retrofit2.HttpException

class SearchPagingSource(
    private val searchQuery: String,
    private val searchFilter: String
): PagingSource<String, ContentItem>() {
    override fun getRefreshKey(state: PagingState<String, ContentItem>): String? {
        return null
    }

    override suspend fun load(params: LoadParams<String>): LoadResult<String, ContentItem> {
        return try {
            val result = params.key?.let {
                RetrofitInstance.api.getSearchResultsNextPage(searchQuery, searchFilter, it)
            } ?: RetrofitInstance.api.getSearchResults(searchQuery, searchFilter)
            LoadResult.Page(result.items.deArrow(), null, result.nextpage)
        } catch (e: HttpException) {
            LoadResult.Error(e)
        }
    }
}
