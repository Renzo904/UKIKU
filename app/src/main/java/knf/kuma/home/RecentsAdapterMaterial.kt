package knf.kuma.home

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import knf.kuma.R
import knf.kuma.animeinfo.ActivityAnimeMaterial
import knf.kuma.backup.firestore.syncData
import knf.kuma.commons.*
import knf.kuma.custom.SeenAnimeOverlay
import knf.kuma.database.CacheDB
import knf.kuma.pojos.RecentObject
import knf.kuma.pojos.SeenObject
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.sdk27.coroutines.onClick
import org.jetbrains.anko.sdk27.coroutines.onLongClick

class RecentsAdapterMaterial(val fragment: HomeFragmentMaterial, private val isLarge: Boolean = true, private val showSeen: Boolean = true) : UpdateableAdapter<RecentsAdapterMaterial.RecentViewHolder>() {

    private var list: List<RecentObject> = emptyList()

    override fun updateList(list: List<Any>) {
        doAsync {
            this@RecentsAdapterMaterial.list = list.transform()
            doOnUI { notifyDataSetChanged() }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentViewHolder = RecentViewHolder(parent.inflate(if (isLarge) R.layout.item_fav_grid_card_material else R.layout.item_fav_grid_card_simple_material))


    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: RecentViewHolder, position: Int) {
        val item = list[position]
        holder.img.load(item.img)
        holder.title.text = item.name
        holder.type?.text = item.chapter
        holder.root.onClick {
            if (item.animeObject != null) {
                ActivityAnimeMaterial.open(fragment, item.animeObject, holder.img)
            } else {
                val animeObject = CacheDB.INSTANCE.animeDAO().getByAid(item.aid)
                if (animeObject != null) {
                    ActivityAnimeMaterial.open(fragment, animeObject, holder.img)
                } else {
                    ActivityAnimeMaterial.open(fragment, item, holder.img)
                }
            }
        }
        if (showSeen) {
            holder.seenOverlay.setSeen(CacheDB.INSTANCE.seenDAO().chapterIsSeen(item.aid, item.chapter), false)
            holder.root.onLongClick(returnValue = true) {
                if (CacheDB.INSTANCE.seenDAO().chapterIsSeen(item.aid, item.chapter)) {
                    CacheDB.INSTANCE.seenDAO().deleteChapter(item.aid, item.chapter)
                    holder.seenOverlay.setSeen(seen = false, animate = true)
                } else {
                    CacheDB.INSTANCE.seenDAO().addChapter(SeenObject.fromRecent(item))
                    holder.seenOverlay.setSeen(seen = true, animate = true)
                }
                syncData { seen() }
            }
        }
    }

    class RecentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val root: View by itemView.bind(R.id.card)
        val img: ImageView by itemView.bind(R.id.img)
        val seenOverlay: SeenAnimeOverlay by itemView.bind(R.id.seenOverlay)
        val title: TextView by itemView.bind(R.id.title)
        val type: TextView? by itemView.optionalBind(R.id.type)
    }
}