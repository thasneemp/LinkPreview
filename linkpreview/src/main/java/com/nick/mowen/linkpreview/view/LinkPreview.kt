package com.nick.mowen.linkpreview.view

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.AsyncTask
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.browser.customtabs.CustomTabsIntent
import com.bumptech.glide.Glide
import com.nick.mowen.linkpreview.ImageType
import com.nick.mowen.linkpreview.R
import com.nick.mowen.linkpreview.extension.isUrl
import com.nick.mowen.linkpreview.listener.LinkClickListener
import com.nick.mowen.linkpreview.listener.LinkListener
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.lang.ref.WeakReference

class LinkPreview : FrameLayout, View.OnClickListener {

    private lateinit var image: ImageView
    private lateinit var text: TextView
    private var linkMap: HashMap<Int, String> = hashMapOf()
    private var imageType = ImageType.NONE
    private var url = ""
    var loadListener: LinkListener? = null
    var clickListener: LinkClickListener? = null
    var articleColor: Int = Color.CYAN

    constructor(context: Context) : super(context) {
        bindViews(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        bindViews(context)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        bindViews(context)
    }

    private fun bindViews(context: Context) {
        val view = LayoutInflater.from(context).inflate(R.layout.preview, this)
        minimumHeight = view.minimumHeight
        minimumWidth = view.minimumWidth

        if (isInEditMode)
            return

        visibility = View.GONE
        image = findViewById(R.id.preview_image)
        text = findViewById(R.id.preview_text)
        setOnClickListener(this)
        //TODO get link hashmap
    }

    /**
     * Handles article clicking based on TYPE of article
     *
     * @param view [LinkPreview] that was clicked
     */
    override fun onClick(view: View?) {
        if (clickListener != null)
            clickListener?.onLinkClicked(view, url)
        else {
            when (imageType) {
                ImageType.DEFAULT -> {
                    val chromeTab = CustomTabsIntent.Builder()
                            .setToolbarColor(articleColor)
                            .addDefaultShareMenuItem()
                            .enableUrlBarHiding()
                            .build()

                    try {
                        chromeTab.launchUrl(context, Uri.parse(url))
                    } catch (e: Exception) {
                        //context.showToast("Could not open article")
                        e.printStackTrace()
                    }
                }
                ImageType.YOUTUBE -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                else -> {
                }
            }
        }
    }

    /**
     * Sets the actual text of the view handling multiple types of images including the link cache
     */
    private fun setText() {
        if (linkMap.containsKey(url.hashCode())) {
            val code = linkMap[url.hashCode()]

            if (code != null && code != "Fail") {
                imageType = ImageType.DEFAULT
                setImageData(code)
            }
        } else {
            if (url.contains("youtube")) {
                val id = url.split("v=")[1].split(" ")[0]
                val imageUrl = "https://img.youtube.com/vi/$id/hqdefault.jpg"
                imageType = ImageType.YOUTUBE
                linkMap[url.hashCode()] = imageUrl
                setImageData(imageUrl)
            } else {
                try {
                    visibility = View.VISIBLE
                    text.text = url
                    imageType = ImageType.DEFAULT
                    ArticleLoadTask(this, linkMap, url.hashCode(), loadListener).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url)
                } catch (e: Exception) {
                    e.printStackTrace()
                    imageType = ImageType.NONE
                    visibility = View.GONE
                    loadListener?.onError()
                }
            }
        }
    }

    /**
     * Handles loading the article image using Glide
     *
     * @param link to image url
     */
    private fun setImageData(link: String) {
        Glide.with(context).load(link).into(image)
        text.text = url

        if (visibility != View.VISIBLE)
            visibility = View.VISIBLE
    }

    /**
     * Allows easy passing of possible link text to check for links that can be handled by [LinkPreview]
     *
     * @param text entire body to search for link
     * @return if a link was found in the text
     */
    fun parseTextForLink(text: String): Boolean {
        when {
            text.contains("youtube") && text.contains("v=") -> {
                val id = text.split("v=")[1].split(" ")[0]
                url = "https://www.youtube.com/watch?v=$id"
                setText()
                return true
            }
            text.contains("youtu.be") -> {
                val id = text.split("be/")[1].split(" ")[0]
                url = "https://www.youtube.com/watch?v=$id"
                setText()
                return true
            }
            text.contains("http") -> {
                text.split(" ").filter { it.matches(Regex("https?://(www\\.)?[-a-zA-Z0-9@:%._+~#=]{2,256}\\.[a-z]{2,6}\\b([-a-zA-Z0-9@:%_+.~#?&/=]*)")) }.forEach { url = it }
                setText()
                return true
            }
            else -> {
                imageType = ImageType.NONE
                visibility = View.GONE
                return false
            }
        }
    }

    /**
     * Allows direct setting of url if already known
     *
     * @param link which contains only the url and nothing else
     */
    fun setLink(link: String) {
        if (link.isUrl()) {
            url = link
            setText()
        } else
            throw IllegalArgumentException("String is not a valid link, if you want to parse full text use LinkPreview.parseTextForLink")
    }

    /**
     * Async task to find and load the url to the image itself, found from the article url
     */
    private class ArticleLoadTask(preview: LinkPreview, private val linkMap: HashMap<Int, String>, private val key: Int, private val listener: LinkListener?) : AsyncTask<String, Void, String>() {

        private val preview = WeakReference(preview)

        /**
         * Loads the article image in the background
         *
         * @param strings [0] holds the url to parse
         * @return parsed image url or "" if exception occurs
         */
        override fun doInBackground(vararg strings: String?): String {
            return try {
                Log.w("Article Request", "Finding article image")
                val connection = Jsoup.connect(strings[0]).userAgent("Mozilla")
                val doc: Document = connection.get()
                val imageElements = doc.select("meta[property=og:image]")

                if (imageElements.size > 0)
                    imageElements[0].attr("content")
                else {
                    linkMap[key] = "Fail"
                    preview.get()?.post {
                        listener?.onError()
                    }
                    ""
                }
            } catch (e: IndexOutOfBoundsException) {
                e.printStackTrace()
                linkMap[key] = "Fail"
                preview.get()?.post {
                    listener?.onError()
                }
                ""
            } catch (e: Exception) {
                e.printStackTrace()
                linkMap[key] = "Fail"
                preview.get()?.post {
                    listener?.onError()
                }
                ""
            }
        }

        /**
         * Handle article image setting on ui thread
         *
         * @param result parsed image url
         */
        override fun onPostExecute(result: String?) {
            try {
                if (result != null && result.isNotEmpty()) {
                    linkMap[key] = result
                    preview.get()?.setImageData(result)
                    listener?.onSuccess(result)
                } else {
                    Log.e("Article Request", "Image url is empty")
                    preview.get()?.visibility = View.GONE
                    listener?.onError()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                listener?.onError()
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
                listener?.onError()
            }
        }
    }
}