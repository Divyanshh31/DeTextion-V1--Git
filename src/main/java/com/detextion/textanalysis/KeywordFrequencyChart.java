package com.detextion.textanalysis;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.chart.ui.RectangleInsets;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Displays a bar chart showing keyword frequency with improved visuals.
 */
public class KeywordFrequencyChart {

    /**
     * Displays a sorted keyword frequency chart.
     *
     * @param frequencyMap Map containing keywords and their frequency counts.
     */
    public static void showChart(Map<String, Integer> frequencyMap) {
        if (frequencyMap == null || frequencyMap.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    "No keyword frequency data available.",
                    "Information",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // ✅ Sort keywords by frequency (descending)
        List<Map.Entry<String, Integer>> sortedEntries = frequencyMap.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(20) // show top 20 keywords
                .collect(Collectors.toList());

        // ✅ Prepare dataset
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (Map.Entry<String, Integer> entry : sortedEntries) {
            dataset.addValue(entry.getValue(), "Frequency", entry.getKey());
        }

        // ✅ Create bar chart
        JFreeChart barChart = ChartFactory.createBarChart(
                "Keyword Frequency Analysis",  // Chart title
                "Keyword",                     // X-axis label
                "Count",                       // Y-axis label
                dataset,                       // Data
                PlotOrientation.VERTICAL,
                false,                         // Legend
                true,                          // Tooltips
                false                          // URLs
        );

        // ✅ Customize plot appearance
        CategoryPlot plot = barChart.getCategoryPlot();
        plot.setBackgroundPaint(new Color(30, 30, 30));
        plot.setDomainGridlinePaint(Color.GRAY);
        plot.setRangeGridlinePaint(Color.GRAY);
        plot.setOutlineVisible(false);
        plot.setInsets(new RectangleInsets(10, 10, 10, 10));

        // ✅ Customize bars
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        Color[] gradientColors = {
                new Color(0, 191, 255),  // DeepSkyBlue
                new Color(0, 140, 255),
                new Color(65, 105, 225),
                new Color(100, 149, 237)
        };

        // Apply gradient colors
        for (int i = 0; i < sortedEntries.size(); i++) {
            renderer.setSeriesPaint(0, gradientColors[i % gradientColors.length]);
        }

        renderer.setDrawBarOutline(false);
        renderer.setShadowVisible(false);

        // ✅ Text styling (Dark mode)
        barChart.setBackgroundPaint(Color.BLACK);
        barChart.getTitle().setPaint(Color.WHITE);
        plot.getDomainAxis().setTickLabelPaint(Color.WHITE);
        plot.getDomainAxis().setLabelPaint(Color.WHITE);
        plot.getRangeAxis().setTickLabelPaint(Color.WHITE);
        plot.getRangeAxis().setLabelPaint(Color.WHITE);

        // ✅ Add chart to a Swing frame
        ChartPanel chartPanel = new ChartPanel(barChart);
        chartPanel.setPreferredSize(new Dimension(900, 600));
        chartPanel.setMouseWheelEnabled(true);

        JFrame frame = new JFrame("Keyword Frequency Chart");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(chartPanel, BorderLayout.CENTER);

        // Header label
        JLabel header = new JLabel("🔍 Keyword Frequency Overview", SwingConstants.CENTER);
        header.setForeground(Color.WHITE);
        header.setFont(new Font("Segoe UI", Font.BOLD, 18));
        header.setBackground(new Color(25, 25, 25));
        header.setOpaque(true);
        frame.add(header, BorderLayout.NORTH);

        frame.getContentPane().setBackground(new Color(20, 20, 20));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
