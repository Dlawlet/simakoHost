package com.hwork.simakohost

import android.content.Context
import android.database.Cursor
import android.graphics.Typeface
import android.net.Uri
import android.provider.ContactsContract
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder


class AllConversationAdapter(private val context: Context, private val data: ArrayList<SMS>?)
    : RecyclerView.Adapter<AllConversationAdapter.MyHolder>() {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyHolder {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.single_sms_small_layout, parent, false)
        return MyHolder(view)
    }

    override fun onBindViewHolder(holder: MyHolder, position: Int) {
        val sms = data!![position]
        val savedContactName = getContactName(sms.address)
        holder.senderContact.text = savedContactName ?: sms.address
        holder.message.text = sms.msg
        
        // Format and display time
        val timeString = DateUtils.getRelativeTimeSpanString(sms.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        holder.time.text = timeString
        
        val color = sms.address.let { 123 }
        sms.color = color
        if (sms.readState == "0") {
            holder.senderContact.setTypeface(holder.senderContact.typeface, Typeface.BOLD)
            holder.message.setTypeface(holder.message.typeface, Typeface.BOLD)
            holder.message.setTextColor(ContextCompat.getColor(context, R.color.black))
            holder.time.setTypeface(holder.time.typeface, Typeface.BOLD)
            holder.time.setTextColor(ContextCompat.getColor(context, R.color.black))
        } else {
            holder.senderContact.setTypeface(null, Typeface.NORMAL)
            holder.message.setTypeface(null, Typeface.NORMAL)
            holder.time.setTypeface(null, Typeface.NORMAL)
        }
    }

    override fun getItemCount(): Int {
        return data?.size ?: 0
    }


    inner class MyHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView),
            View.OnClickListener,
            OnLongClickListener {

        val senderImage: ImageView = itemView.findViewById(R.id.smsImage)
        val senderContact: TextView = itemView.findViewById(R.id.smsSender)
        val message: TextView = itemView.findViewById(R.id.smsContent)
        val time: TextView = itemView.findViewById(R.id.time)
        private val mainLayout: RelativeLayout = itemView.findViewById(R.id.small_layout_main)

        override fun onClick(view: View) {
                data!![adapterPosition].readState = "1"
                notifyItemChanged(adapterPosition)
        }

        override fun onLongClick(view: View): Boolean {
            val items = arrayOf("Delete")
            val adapter = ArrayAdapter(context
                    , android.R.layout.simple_list_item_1, android.R.id.text1, items)
            MaterialAlertDialogBuilder(context)
                    .setAdapter(adapter) { dialogInterface, _ ->
                        dialogInterface.dismiss()
                        deleteDialog()
                    }
                    .show()
            return true
        }

        private fun deleteDialog() {
            val alert = MaterialAlertDialogBuilder(context)
            alert.setMessage("Are you sure you want to delete this message?")
            alert.setPositiveButton("Yes") { _, _ -> data!![adapterPosition]?.id?.let { deleteSMS(it, adapterPosition) } }
            alert.setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
            alert.create()
            alert.show()
        }

        init {
            mainLayout.setOnClickListener(this)
            mainLayout.setOnLongClickListener(this)
        }
    }

    private fun deleteSMS(messageId: Long, position: Int) {
        val affected = context.contentResolver.delete(
                Uri.parse("content://sms/$messageId"), null, null).toLong()
        if (affected != 0L) {
            data?.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    private fun getContactName(number: String?): String? {
        var c: Cursor? = null
        var cName: String? = null
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            val nameColumn = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            c = context.contentResolver.query(uri, nameColumn, null, null, null)
            cName = if (c == null || c.count == 0) {
                number
            } else {
                c.moveToFirst()
                c.getString(0)
            }
        } catch (e: Exception) {
            cName = number
        } finally {
            if (c != null && !c.isClosed) {
                c.close()
            }
        }
        return cName
    }

}