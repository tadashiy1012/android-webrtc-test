package jp.yama.webrtctest001

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class MyAdapter(val ctx: Context): ListAdapter<Media, MyAdapter.MyViewHolder>(diffCallback) {

    class MyViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val container: FrameLayout
        init {
            container = view.findViewById(R.id.container)
        }
    }

    companion object {
        val diffCallback = object : DiffUtil.ItemCallback<Media>() {
            override fun areItemsTheSame(oldItem: Media, newItem: Media): Boolean {
                return oldItem == newItem
            }
            override fun areContentsTheSame(oldItem: Media, newItem: Media): Boolean {
                return oldItem.id == newItem.id
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val inflater = LayoutInflater.from(ctx)
        val itemView = inflater.inflate(R.layout.media_item, parent, false)
        return MyViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val item = getItem(position)
        when (item.type) {
            "text" -> holder.container.addView(TextView(ctx).apply {
                this.text = item.text
            })
            "picture" -> holder.container.addView(ImageView(ctx).apply {
                this.setImageBitmap(item.bitmap)
            })
            else -> {}
        }
    }

}