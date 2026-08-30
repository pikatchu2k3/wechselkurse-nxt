package de.salomax.currencies.view.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import de.salomax.currencies.R
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.Rate
import de.salomax.currencies.util.getSignificantDecimalPlaces
import de.salomax.currencies.util.toHumanReadableNumber

class RatesListAdapter(private val onRowClick: (Currency) -> Unit) :
    RecyclerView.Adapter<RatesListAdapter.ViewHolder>() {

    private data class Row(val rate: Rate, val isHome: Boolean)

    private var rows: List<Row> = emptyList()
    private var baseCurrency: Currency? = null
    private var baseRateValue: Float = 1f
    private var baseValue: Double = 1.0

    fun setItems(rates: List<Rate>, baseCurrency: Currency?) {
        this.rows = rates.map { Row(it, it.currency == baseCurrency) }
        this.baseCurrency = baseCurrency
        this.baseRateValue = rates.find { it.currency == baseCurrency }?.value
            ?.takeIf { it != 0f }
            ?: 1f
        notifyDataSetChanged()
    }

    fun setBaseValue(baseValue: Double) {
        val value = if (baseValue > 0.0) baseValue else 1.0
        if (this.baseValue == value) return
        this.baseValue = value
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ShapeableImageView = view.findViewById(R.id.image)
        val textName: TextView = view.findViewById(R.id.textName)
        val textSubtitle: TextView = view.findViewById(R.id.textSubtitle)
        val textAmount: TextView = view.findViewById(R.id.textAmount)
        val imageHome: ImageView = view.findViewById(R.id.imageHome)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_currency_main, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return rows.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = rows[position]
        val context = holder.itemView.context
        val currency = row.rate.currency

        holder.image.setImageDrawable(currency.icon(context))
        holder.textName.text = currency.fullName(context)
        holder.imageHome.visibility = if (row.isHome) View.VISIBLE else View.GONE

        holder.textSubtitle.text =
            if (row.isHome)
                currency.iso4217Alpha()
            else
                context.getString(
                    R.string.row_conversion,
                    baseCurrency?.iso4217Alpha() ?: "",
                    row.rate.value.toHumanReadableNumber(
                        context,
                        decimalPlaces = row.rate.value.getSignificantDecimalPlaces(4),
                        trim = true
                    ),
                    currency.iso4217Alpha()
                )

        val amount = (baseValue / baseRateValue * row.rate.value).toFloat()
        val amountPrefix = currency.symbol() ?: currency.unitLabel() ?: currency.iso4217Alpha()
        holder.textAmount.text = context.getString(
            R.string.row_amount,
            amountPrefix,
            amount.toHumanReadableNumber(
                context,
                decimalPlaces = amount.getSignificantDecimalPlaces(3),
                trim = true
            )
        )

        holder.itemView.setOnClickListener { onRowClick(currency) }
    }

}
