package com.stash.opusplayer.ui.widgets

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Same column-spacing math as [GridSpacingItemDecoration], adjusted for a
 * single full-span header item at adapter position 0 (see
 * `MusicLibraryFragment`'s `ConcatAdapter`/`ShelvesHeaderAdapter` usage).
 *
 * [GridSpacingItemDecoration] computes `column = position % spanCount`,
 * which is only correct when every item has the same span size. Once a
 * full-row header sits at position 0, the first real grid item is at
 * global position 1 but visually starts a fresh row at column 0 -- the
 * naive formula would compute `1 % spanCount` instead, shifting every
 * row's spacing pattern by one column. This subtracts the header count
 * before applying the same math, and gives the header item itself zero
 * extra offset (its own layout already provides spacing).
 */
class HeaderAwareGridSpacingItemDecoration(
    private val spanCount: Int,
    private val spacingPx: Int,
    private val includeEdge: Boolean,
    private val headerCount: Int = 1
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        if (position < headerCount) {
            outRect.setEmpty()
            return
        }
        val gridPosition = position - headerCount
        val column = gridPosition % spanCount

        if (includeEdge) {
            outRect.left = spacingPx - column * spacingPx / spanCount
            outRect.right = (column + 1) * spacingPx / spanCount
            if (gridPosition < spanCount) {
                outRect.top = spacingPx
            }
            outRect.bottom = spacingPx
        } else {
            outRect.left = column * spacingPx / spanCount
            outRect.right = spacingPx - (column + 1) * spacingPx / spanCount
            if (gridPosition >= spanCount) {
                outRect.top = spacingPx
            }
        }
    }
}
