package de.salomax.currencies.view.main

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import de.salomax.currencies.R
import de.salomax.currencies.model.AssetCategory
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.Rate
import de.salomax.currencies.util.getSignificantDecimalPlaces
import de.salomax.currencies.util.hasAppendedCurrencySymbol
import de.salomax.currencies.util.perUnitLabel
import de.salomax.currencies.util.toHumanReadableNumber

/**
 * Adapter of the add-currency dialog: shows the entries of the currently selected category
 * (currencies / crypto / commodities / precious metals) and supports live-filtering by full name
 * or ISO code while the user types. The category is driven by the dialog's category chips
 * (see [setCategory]/[getCategories]).
 */
@SuppressLint("NotifyDataSetChanged")
class AddCurrencyDialogAdapter(private val context: Context) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // listeners
    var onItemToggled: ((Currency) -> Unit)? = null

    // picker categories (order = chip order in the dialog)
    enum class AddGroup {
        CURRENCIES, CRYPTO, COMMODITIES, METALS
    }

    private var groups: Map<AddGroup, List<Currency>> = emptyMap()
    private var rows: List<Currency> = emptyList()
    private var stars: Set<Currency> = emptySet()
    private var filterText: String? = null
    private var selectedGroup: AddGroup = AddGroup.CURRENCIES

    private var values: Map<Currency, Float> = emptyMap()
    private var baseEurValue: Float = 1f

    private val drawableStar = ContextCompat.getDrawable(context, R.drawable.ic_favorite)
    private val drawableStarEmpty = ContextCompat.getDrawable(context, R.drawable.ic_favorite_empty)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ViewHolderEntry(
            LayoutInflater.from(context).inflate(R.layout.row_currency_dropdown, parent, false)
        )
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ViewHolderEntry).bind(rows[position])
    }

    override fun getItemCount(): Int {
        return rows.size
    }

    fun setRates(rates: List<Rate>?) {
        values = rates?.associate { it.currency to it.value } ?: emptyMap()
        baseEurValue = values[Currency.EUR]?.takeIf { it != 0f } ?: 1f
        groups = (rates?.map { it.currency } ?: emptyList())
            .distinct()
            .groupBy { groupOf(it) }
            // fiat: by code (that's what people search for). everything else: by name -
            // the pseudo-codes of commodities (XCF, XSY, ...) mean nothing to anybody
            .mapValues { (group, currencies) ->
                if (group == AddGroup.CURRENCIES)
                    currencies.sortedBy { it.iso4217Alpha() }
                else
                    currencies.sortedBy { it.fullName(context) }
            }
        // the selected category may have no entries (e.g. provider without brent oil) → fall back
        if (groups[selectedGroup].isNullOrEmpty()) {
            selectedGroup = getCategories().firstOrNull() ?: AddGroup.CURRENCIES
        }
        rebuild()
    }

    fun setStars(stars: Set<Currency>?) {
        this.stars = stars ?: emptySet()
        rebuild()
    }

    fun filter(query: String?) {
        filterText = query?.trim()?.takeIf { it.isNotEmpty() }
        rebuild()
    }

    // the picker's category tabs: only categories that currently have entries, in chip order
    fun getCategories(): List<AddGroup> {
        return AddGroup.entries.filter { groups[it].orEmpty().isNotEmpty() }
    }

    // show only this category's entries (no section headers — the chip is the header)
    fun setCategory(group: AddGroup) {
        selectedGroup = group
        rebuild()
    }

    // live search: substring match against the localized full name AND the ISO code
    private fun matches(currency: Currency): Boolean {
        val query = filterText ?: return true
        return currency.fullName(context).contains(query, ignoreCase = true)
            || currency.iso4217Alpha().contains(query, ignoreCase = true)
    }

    // rebuild the visible rows: the selected category's entries, filtered by the live search
    private fun rebuild() {
        rows = groups[selectedGroup].orEmpty().filter { matches(it) }
        notifyDataSetChanged()
    }

    /**
     * classify a [Currency] into its picker category, see [Currency.category]
     */
    private fun groupOf(currency: Currency): AddGroup {
        return when (currency.category()) {
            AssetCategory.FIAT -> AddGroup.CURRENCIES
            AssetCategory.CRYPTO -> AddGroup.CRYPTO
            AssetCategory.COMMODITY -> AddGroup.COMMODITIES
            AssetCategory.METAL -> AddGroup.METALS
        }
    }

    /**
     * "€ 1 = $ 1.09" for currencies. Assets are quoted the other way round, as everybody knows
     * them: the price of one unit ("1 BTC = € 68,024.08", "1 oz t = € 2,912.40"), instead of
     * "€ 1 = ₿ 0.0000147".
     */
    private fun describeRate(currency: Currency): String {
        val eurSymbol = Currency.EUR.symbol() ?: ""
        val value = values[currency] ?: 0f

        fun money(number: String) =
            if (eurSymbol.isEmpty()) number
            else if (hasAppendedCurrencySymbol(context)) "$number $eurSymbol" else "$eurSymbol $number"

        val text = if (currency.isAsset() && value > 0f) {
            val price = baseEurValue / value
            val left = "1 ${currency.perUnitLabel()}"
            val right = money(
                price.toHumanReadableNumber(
                    context,
                    decimalPlaces = price.getSignificantDecimalPlaces(2),
                    trim = true
                )
            )
            "$left = $right"
        } else {
            val destinationSymbol = currency.symbol() ?: ""
            val rawDestination = (1.0 / baseEurValue * value).toFloat()
            val destination = rawDestination.toHumanReadableNumber(
                context,
                decimalPlaces = rawDestination.getSignificantDecimalPlaces(2),
                trim = true
            )
            val left =
                if (eurSymbol.isEmpty()) "1"
                else if (hasAppendedCurrencySymbol(context)) "1 $eurSymbol" else "$eurSymbol 1"
            val right =
                if (destinationSymbol.isEmpty()) destination
                else if (hasAppendedCurrencySymbol(context)) "$destination $destinationSymbol" else "$destinationSymbol $destination"
            "$left = $right"
        }
        return text.replace("\u200F", "").trim()
    }

    inner class ViewHolderEntry(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val ivFlag: ShapeableImageView = itemView.findViewById(R.id.image)
        val tvCode: TextView = itemView.findViewById(R.id.text2)
        val tvName: TextView = itemView.findViewById(R.id.text)
        val tvRate: TextView = itemView.findViewById(R.id.text3)
        val btnStar: ImageButton = itemView.findViewById(R.id.btn_fav)

        fun bind(currency: Currency) {
            // asset icons (bitcoin/gold/silver/oil) instead of the gray flag_unknown globe
            ivFlag.setImageDrawable(currency.icon(context))
            // ISO 4217 currency code ("XAU")
            tvCode.text = currency.iso4217Alpha()
            // full name ("Gold Ounce")
            tvName.text = currency.fullName(context)
            tvRate.text = describeRate(currency)
            // selected state: starred = added to the rates list
            btnStar.setImageDrawable(
                if (currency in stars) drawableStar else drawableStarEmpty
            )
        }

        init {
            // tapping the row or the star toggles the currency (add/remove)
            val toggle = {
                rows.getOrNull(layoutPosition)?.let { onItemToggled?.invoke(it) }
            }
            itemView.setOnClickListener { toggle() }
            btnStar.setOnClickListener { toggle() }
        }
    }

}
