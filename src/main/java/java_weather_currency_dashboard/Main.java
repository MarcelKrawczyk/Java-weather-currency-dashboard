package java_weather_currency_dashboard;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

public class Main {
    private static WebEngine webEngine;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::buildUI);
    }

    private static void buildUI() {

        JFrame frame = new JFrame("Weather & Currency Info");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 700);
        frame.setMinimumSize(new Dimension(900, 600));
        frame.setLayout(new BorderLayout(8, 8));

        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        inputPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        JTextField cityField = new JTextField("Warsaw", 14);
        JTextField countryField = new JTextField("Poland", 14);
        JTextField targetCurrencyField = new JTextField("USD", 6);
        JButton fetchButton = new JButton("Fetch");
        JLabel statusLabel = new JLabel(" ");
        statusLabel.setForeground(Color.GRAY);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11f));

        inputPanel.add(new JLabel("City:"));
        inputPanel.add(cityField);
        inputPanel.add(new JLabel("Country:"));
        inputPanel.add(countryField);
        inputPanel.add(new JLabel("Target currency:"));
        inputPanel.add(targetCurrencyField);
        inputPanel.add(fetchButton);
        inputPanel.add(statusLabel);

        JTextArea resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        resultArea.setMargin(new Insets(8, 10, 8, 10));
        resultArea.setText("Enter a city and country above, then click Fetch.");

        JScrollPane resultScroll = new JScrollPane(resultArea);
        resultScroll.setBorder(BorderFactory.createTitledBorder("Results"));
        resultScroll.setPreferredSize(new Dimension(420, 0));

        JFXPanel browserPanel = new JFXPanel();
        browserPanel.setBorder(BorderFactory.createTitledBorder("Wikipedia"));

        Platform.runLater(() -> {
            WebView view = new WebView();
            webEngine = view.getEngine();
            webEngine.load("https://en.wikipedia.org/wiki/Warsaw");
            browserPanel.setScene(new Scene(view));
        });

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, resultScroll, browserPanel);
        splitPane.setResizeWeight(0.35);
        splitPane.setDividerSize(6);

        frame.add(inputPanel, BorderLayout.NORTH);
        frame.add(splitPane,  BorderLayout.CENTER);

        fetchButton.addActionListener(e -> {
            String city = cityField.getText().trim();
            String country = countryField.getText().trim();
            String targetCurrency = targetCurrencyField.getText().trim().toUpperCase();

            if (city.isEmpty() || country.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Please enter both a city and a country.", "Missing input", JOptionPane.WARNING_MESSAGE);
                return;
            }

            fetchButton.setEnabled(false);
            statusLabel.setText("Fetching data…");
            resultArea.setText("Loading…");

            SwingWorker<String, Void> worker = new SwingWorker<>() {

                @Override
                protected String doInBackground() {
                    Service service = new Service(country);
                    String currency = service.getCurrencyCode();
                    StringBuilder sb = new StringBuilder();

                    sb.append("=======================================\n");
                    sb.append("  WEATHER  —  ").append(city).append(", ").append(country).append("\n");
                    sb.append("=======================================\n");
                    try {
                        sb.append(service.getWeatherSummary(city));
                    } catch (Exception ex) {
                        sb.append("Error: ").append(ex.getMessage());
                    }

                    sb.append("\n\n=======================================\n");
                    sb.append("  EXCHANGE RATE  (Fixer.io)\n");
                    sb.append("=======================================\n");
                    try {
                        Double rate = service.getRateFor(targetCurrency);
                        if (rate != null) {
                            sb.append(String.format("1 %s  =  %.4f %s%n", currency, rate, targetCurrency));
                        } else {
                            sb.append("Rate unavailable (check currency codes or API key).\n");
                        }
                    } catch (Exception ex) {
                        sb.append("Error: ").append(ex.getMessage()).append("\n");
                    }

                    sb.append("\n=======================================\n");
                    sb.append("  NBP RATE  (PLN → ").append(currency).append(")\n");
                    sb.append("=======================================\n");
                    try {
                        if (currency.equals("PLN")) {
                            sb.append("Selected country uses PLN — no NBP rate needed.\n");
                        } else {
                            Double nbp = service.getNBPRate();
                            if (nbp != null) {
                                sb.append(String.format("1 %s  =  %.4f PLN  (NBP mid-rate)%n", currency, nbp));
                            } else {
                                sb.append("NBP rate not available for ").append(currency).append(".\n");
                            }
                        }
                    } catch (Exception ex) {
                        sb.append("Error: ").append(ex.getMessage()).append("\n");
                    }

                    return sb.toString();
                }

                @Override
                protected void done() {
                    try {
                        resultArea.setText(get());
                        resultArea.setCaretPosition(0);
                        statusLabel.setText("Done.");
                    } catch (Exception ex) {
                        resultArea.setText("Unexpected error: " + ex.getMessage());
                        statusLabel.setText("Error.");
                    }
                    fetchButton.setEnabled(true);

                    String wikiUrl = new Service(country).getWikiUrl(city);
                    Platform.runLater(() -> {
                        if (webEngine != null) webEngine.load(wikiUrl);
                    });
                }
            };

            worker.execute();
        });

        ActionListener enterAction = e -> fetchButton.doClick();
        cityField.addActionListener(enterAction);
        countryField.addActionListener(enterAction);
        targetCurrencyField.addActionListener(enterAction);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}