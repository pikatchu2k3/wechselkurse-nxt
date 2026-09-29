package de.salomax.currencies.view.main

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.imageview.ShapeableImageView
import de.salomax.currencies.R
import de.salomax.currencies.model.AssetCategory
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.Rate
import de.salomax.currencies.util.getSignificantDecimalPlaces
import de.salomax.currencies.util.perUnitLabel
import de.salomax.currencies.util.toHumanReadableNumber

/**
 * The rates list: rows grouped by category (currencies / crypto / precious metals / commodities).
 * The category headings only show up when the list actually mixes categories.
 */
class RatesListAdapter(
    private val context: Context,
    private val onRowClick: (Currency) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Item {
        data class Header(val category: AssetCategory) : Item()
        data class Row(val rate: Rate, val isHome: Boolean) : Item()
    }

    private var items: List<Item> = emptyList()
    private var baseCurrency: Currency? = null
    private var baseRateValue: Float = 1f
    private var baseValue: Double = 1.0

    fun setItems(rates: List<Rate>, baseCurrency: Currency?, baseRateValue: Float) {
        val byCategory = rates
            .groupBy { it.currency.category() }
            .toSortedMap()
        val showHeaders = byCategory.size > 1

        this.items = byCategory.flatMap { (category, categoryRates) ->
            val rows = categoryRates
                // home currency on top, then: fiat by code, everything else by name (their
                // pseudo-codes like "XCF" mean nothing to anybody)
                .sortedWith(
                    compareByDescending<Rate> { it.currency == baseCurrency }
                        .thenBy {
                            if (category == AssetCategory.FIAT) it.currency.iso4217Alpha()
                            else it.currency.fullName(context)
                        }
                )
                .map { Item.Row(it, it.currency == baseCurrency) }
            if (showHeaders) listOf<Item>(Item.Header(category)) + rows else rows
        }
        this.baseCurrency = baseCurrency
        // the true stored value of the base currency (from the full snapshot) - never
        // derived from the (star-filtered) rows, where the base may be missing entirely
        this.baseRateValue = baseRateValue.takeIf { it != 0f } ?: 1f
        notifyDataSetChanged()
    }

    fun setBaseValue(baseValue: Double) {
        val value = if (baseValue > 0.0) baseValue else 1.0
        if (this.baseValue == value) return
        this.baseValue = value
        notifyDataSetChanged()
    }

    /**
     * rebind all rows: e.g. to pick up edited amounts when returning from the "change amount" screen
     */
    fun refresh() {
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.card)
        val image: ShapeableImageView = view.findViewById(R.id.image)
        val textName: TextView = view.findViewById(R.id.textName)
        val textSubtitle: TextView = view.findViewById(R.id.textSubtitle)
        val textAmount: TextView = view.findViewById(R.id.textAmount)
        val imageHome: ImageView = view.findViewById(R.id.imageHome)
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textHeader: TextView = view.findViewById(R.id.textHeader)
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is Item.Header) VIEW_TYPE_HEADER else VIEW_TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER)
            HeaderViewHolder(inflater.inflate(R.layout.row_section_header, parent, false))
        else
            ViewHolder(inflater.inflate(R.layout.row_currency_main, parent, false))
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Header -> bindHeader(holder as HeaderViewHolder, item)
            is Item.Row -> bindRow(holder as ViewHolder, item)
        }
    }

    private fun bindHeader(holder: HeaderViewHolder, header: Item.Header) {
        holder.textHeader.setText(
            when (header.category) {
                AssetCategory.FIAT -> R.string.add_currency_section_currencies
                AssetCategory.CRYPTO -> R.string.add_currency_section_crypto
                AssetCategory.METAL -> R.string.add_currency_section_metals
                AssetCategory.COMMODITY -> R.string.add_currency_section_commodities
            }
        )
    }

    private fun bindRow(holder: ViewHolder, row: Item.Row) {
        val context = holder.itemView.context
        val currency = row.rate.currency

        holder.image.setImageDrawable(currency.icon(context))
        holder.textName.text = currency.fullName(context)
        holder.imageHome.visibility = if (row.isHome) View.VISIBLE else View.GONE
        styleCard(holder.card, row.isHome)

        val amountPrefix = currency.symbol() ?: currency.unitLabel() ?: currency.iso4217Alpha()
        // the home row always shows the current base value; every other row is derived from the
        // base-value/basis-rate ratio, so the whole list stays linked to the last calculated
        // currency (no per-currency pinned amounts that get out of sync)
        val amount = if (row.isHome)
            baseValue.toFloat()
        else
            (baseValue / baseRateValue * row.rate.value).toFloat()

        holder.textSubtitle.text = when {
            row.isHome -> currency.iso4217Alpha()
            // assets are quoted by their price, as everybody knows them: "1 BTC = 68.024 EUR"
            // instead of "0,05 EUR = 0,0000007 BTC"
            currency.isAsset() && baseCurrency?.isAsset() != true && row.rate.value > 0f -> {
                val price = baseRateValue / row.rate.value
                context.getString(
                    R.string.row_conversion,
                    "1",
                    currency.perUnitLabel(),
                    price.toHumanReadableNumber(
                        context,
                        decimalPlaces = price.getSignificantDecimalPlaces(2),
                        trim = true
                    ),
                    baseCurrency?.iso4217Alpha() ?: ""
                )
            }
            else -> context.getString(
                R.string.row_conversion,
                baseValue.toFloat().toHumanReadableNumber(
                    context,
                    decimalPlaces = baseValue.toFloat().getSignificantDecimalPlaces(4),
                    trim = true
                ),
                baseCurrency?.iso4217Alpha() ?: "",
                amount.toHumanReadableNumber(
                    context,
                    decimalPlaces = amount.getSignificantDecimalPlaces(3),
                    trim = true
                ),
                currency.iso4217Alpha()
            )
        }

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

    /**
     * neutral rows: a faint tint of the text color. The home row: a tint of the primary color
     * plus a thin outline. Both derive from theme attributes, so they follow light / dark / dynamic colors.
     */
    private fun styleCard(card: MaterialCardView, isHome: Boolean) {
        if (isHome) {
            val primary = MaterialColors.getColor(card, R.attr.colorPrimary)
            card.setCardBackgroundColor(ColorUtils.setAlphaComponent(primary, ALPHA_HOME))
            card.strokeColor = primary
            card.strokeWidth = (card.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        } else {
            val onSurface = MaterialColors.getColor(card, R.attr.colorOnSurface)
            card.setCardBackgroundColor(ColorUtils.setAlphaComponent(onSurface, ALPHA_NEUTRAL))
            card.strokeWidth = 0
        }
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_ROW = 1

        // 0..255
        const val ALPHA_NEUTRAL = 16
        const val ALPHA_HOME = 56
    }

}
