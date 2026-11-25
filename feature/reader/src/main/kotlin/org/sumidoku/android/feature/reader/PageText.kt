package org.sumidoku.android.feature.reader

import org.sumidoku.android.core.api.model.Page

/** Text a page contributes to the reader, mirroring the webui contract in
 *  `webui/src/pages/reader/pageText.ts`.
 *
 *  A source may deliver a page as a description instead of inline text —
 *  EPUB table-of-contents entries and OPDS synopsis pages do — and the web
 *  reader renders that description as the page body. Precedence is therefore
 *  description first, then `content.Text`, and `null` when the page is an
 *  image (or a blob the source has not materialised yet).
 *
 *  Keeping this in one place means the reader detects prose chapters and
 *  draws them from the same rule the web client uses. */
fun Page.pageText(): String? {
    if (hasDescription && description != null) return description
    return content.text
}
