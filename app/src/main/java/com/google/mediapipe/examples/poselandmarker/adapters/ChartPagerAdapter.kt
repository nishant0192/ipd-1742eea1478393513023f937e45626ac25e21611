package com.google.mediapipe.examples.poselandmarker.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.Chart
import com.google.mediapipe.examples.poselandmarker.R
import java.util.ArrayList

/**
 * Adapter for the chart ViewPager, handles different charts as pages
 */
class ChartPagerAdapter(private val context: Context) : 
    RecyclerView.Adapter<ChartPagerAdapter.ChartViewHolder>() {
    
    // List to hold chart views and their titles
    private val charts = ArrayList<Pair<String, Chart<*>>>()
    
    /**
     * Add a chart to the pager
     */
    fun addChart(title: String, chart: Chart<*>) {
        charts.add(Pair(title, chart))
        notifyItemInserted(charts.size - 1)
    }
    
    /**
     * Get the title for a chart at a specific position
     */
    fun getChartTitle(position: Int): String {
        return charts.getOrNull(position)?.first ?: "Chart"
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChartViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_chart, parent, false)
        return ChartViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ChartViewHolder, position: Int) {
        val chartItem = charts[position]
        val chartView = chartItem.second
        
        // Remove chart from its current parent if it has one
        val chartParent = chartView.parent as? ViewGroup
        chartParent?.removeView(chartView)
        
        // Add chart to the container
        holder.chartContainer.removeAllViews() // Clear any previous views
        holder.chartContainer.addView(chartView)
        
        // IMPORTANT: Use FrameLayout.LayoutParams instead of ViewGroup.LayoutParams
        // to fix the ClassCastException
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        chartView.layoutParams = layoutParams
    }
    
    override fun getItemCount(): Int = charts.size
    
    /**
     * ViewHolder for chart pages
     */
    class ChartViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val chartContainer: FrameLayout = itemView.findViewById(R.id.chart_container)
    }
}