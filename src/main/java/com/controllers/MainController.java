package com.controllers;

import com.detextion.textanalysis.KeywordFrequencyChart;
import javafx.animation.*;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class MainController {

    // ========================= UI COMPONENTS =========================
    @FXML private ToolBar mainToolbar;
    @FXML private SplitPane editorFrame;
    @FXML private Button openEditorButton;
    @FXML private TextArea textAreaContent;
    @FXML private Pane graphPane;
    @FXML private VBox mainVBox;

    // === Connection Inspector Panel ===
    @FXML private AnchorPane connectionInspector;
    @FXML private VBox inspectorBox;
    @FXML private Label inspectorTitle;
    @FXML private Label inspectorSubtitle;
    @FXML private ListView<String> sharedKeywordList;

    // === Data ===
    private final Map<String, Set<String>> pdfKeywords = new HashMap<>();
    private final Map<String, Integer> keywordFrequency = new HashMap<>();
    private final Map<String, Circle> nodeMap = new HashMap<>();

    // === Graph root (for zoom & pan) ===
    private final Group graphGroup = new Group();
    private double mouseX, mouseY;

    // ===========================================================
    // Physics (Force-directed) — Option 1 added
    // ===========================================================
    private static class NodeBody {
        final String id;
        final Circle circle;
        double vx = 0, vy = 0;
        final double mass = 1.0;
        NodeBody(String id, Circle c) { this.id = id; this.circle = c; }
        double x() { return circle.getCenterX(); }
        double y() { return circle.getCenterY(); }
        void set(double x, double y) { circle.setCenterX(x); circle.setCenterY(y); }
    }
    private static class EdgeBody {
        final NodeBody a, b;
        final Line line;
        final double k;        // spring constant
        final double restLen;  // natural length
        EdgeBody(NodeBody a, NodeBody b, Line line, double k, double restLen) {
            this.a = a; this.b = b; this.line = line; this.k = k; this.restLen = restLen;
        }
    }

    private final Map<String, NodeBody> physNodes = new HashMap<>();
    private final List<EdgeBody> physEdges = new ArrayList<>();
    private Timeline forceTimeline;
    private boolean physicsEnabled = true;

    // Tunables
    private double repulsionK = 2800;   // Coulomb constant
    private double springBaseK = 0.08;  // Hooke base (scaled by edge weight)
    private double damping = 0.85;      // velocity damping
    private double timeStep = 0.016;    // ~60 FPS
    private double minRestLen = 140;    // min spring length
    private double maxRestLen = 240;    // max spring length
    private double repelClamp = 900;    // cap repulsive force
    private double maxSpeed = 900;      // clamp speed

    // ===========================================================
    // INIT
    // ===========================================================
    @FXML
    public void initialize() {
        // Add graph group to pane
        graphPane.getChildren().add(graphGroup);

        // ✅ Ensure toolbar is hidden until editor is opened
        if (mainToolbar != null) {
            mainToolbar.setVisible(false);
            mainToolbar.setManaged(false);
        }

        if (editorFrame != null) {
            editorFrame.setVisible(false);
            editorFrame.setManaged(false);
        }

        // Zoom
        graphPane.setOnScroll(e -> {
            double scale = graphGroup.getScaleX() == 0 ? 1.0 : graphGroup.getScaleX();
            double delta = e.getDeltaY() > 0 ? 1.1 : 0.9;
            graphGroup.setScaleX(scale * delta);
            graphGroup.setScaleY(scale * delta);
        });

        // Pan
        graphPane.setOnMousePressed(e -> { mouseX = e.getSceneX(); mouseY = e.getSceneY(); });
        graphPane.setOnMouseDragged(e -> {
            double dx = e.getSceneX() - mouseX;
            double dy = e.getSceneY() - mouseY;
            graphGroup.setTranslateX(graphGroup.getTranslateX() + dx);
            graphGroup.setTranslateY(graphGroup.getTranslateY() + dy);
            mouseX = e.getSceneX(); mouseY = e.getSceneY();
        });

        // Subtle UI entrance
        if (mainVBox != null) {
            FadeTransition fadeIn = new FadeTransition(Duration.seconds(1.5), mainVBox);
            fadeIn.setFromValue(0); fadeIn.setToValue(1); fadeIn.play();
        }

        if (openEditorButton != null) {
            ScaleTransition pulse = new ScaleTransition(Duration.seconds(2), openEditorButton);
            pulse.setFromX(1); pulse.setFromY(1);
            pulse.setToX(1.05); pulse.setToY(1.05);
            pulse.setCycleCount(Animation.INDEFINITE);
            pulse.setAutoReverse(true);
            pulse.play();
        }
    }



    // ===========================================================
    // Option 1 — FXML handler fix for "Open Text Editor" button
    // ===========================================================
    @FXML
    private void handleOpenEditor() {
        if (mainToolbar != null) {
            mainToolbar.setVisible(true);
            mainToolbar.setManaged(true);
        }

        if (editorFrame != null) {
            editorFrame.setVisible(true);
            editorFrame.setManaged(true);
        }

        if (openEditorButton != null) {
            openEditorButton.setVisible(false);
            openEditorButton.setManaged(false);
        }

        FadeTransition fade = new FadeTransition(Duration.seconds(0.7), editorFrame);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }


    // ===========================================================
    // PDF HANDLING
    // ===========================================================
    @FXML private void onUploadClicked() { processPDFs(false); }
    @FXML private void onUploadMultipleClicked() { processPDFs(true); }

    private void processPDFs(boolean multiple) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

        List<File> files = multiple
                ? chooser.showOpenMultipleDialog(null)
                : Optional.ofNullable(chooser.showOpenDialog(null)).map(List::of).orElse(Collections.emptyList());

        if (files == null || files.isEmpty()) return;

        pdfKeywords.clear();
        keywordFrequency.clear();
        textAreaContent.clear();
        nodeMap.clear();

        for (File file : files) {
            try (PDDocument doc = PDDocument.load(file)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String content = stripper.getText(doc);
                textAreaContent.appendText("=== " + file.getName() + " ===\n" + content + "\n\n");

                Map<String, Integer> freqMap = extractKeywordFrequency(content);
                freqMap.forEach((k, v) -> keywordFrequency.merge(k, v, Integer::sum));
                pdfKeywords.put(file.getName(), freqMap.keySet());

            } catch (IOException e) {
                textAreaContent.appendText("\n⚠ Error reading " + file.getName() + ": " + e.getMessage() + "\n");
            }
        }

        graphGroup.getChildren().clear();
        stopForceSimulation(); // reset physics

        if (files.size() == 1)
            generateKeywordGraph(pdfKeywords);
        else
            generateConnectionGraph(pdfKeywords); // physics auto-starts
    }

    private Map<String, Integer> extractKeywordFrequency(String text) {
        Set<String> stopWords = Set.of(
                "this","that","with","from","have","were","there","their","been","about","which","also","some","will",
                "into","your","the","and","for","are","was","you","but","not","can","all","any","has","they"
        );

        List<String> words = Arrays.stream(text.split("\\W+"))
                .map(String::toLowerCase)
                .filter(w -> w.length() > 3 && !stopWords.contains(w))
                .toList();

        Map<String, Integer> freq = new HashMap<>();
        for (String w : words) freq.put(w, freq.getOrDefault(w, 0) + 1);

        return freq.entrySet().stream()
                .sorted(Map.Entry.<String,Integer>comparingByValue().reversed())
                .limit(15)
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (a,b) -> a, LinkedHashMap::new
                ));
    }

    // ===========================================================
    // SINGLE PDF GRAPH (kept as your modern radial)
    // ===========================================================
    private void generateKeywordGraph(Map<String, Set<String>> map) {
        graphGroup.getChildren().clear();
        if (map.isEmpty()) return;

        String pdfName = map.keySet().iterator().next();
        Set<String> keywords = map.get(pdfName);

        double width = graphPane.getWidth() > 0 ? graphPane.getWidth() : 900;
        double height = graphPane.getHeight() > 0 ? graphPane.getHeight() : 600;
        double centerX = width / 2, centerY = height / 2;

        Circle centerNode = new Circle(centerX, centerY, 60, Color.web("#00BFFF"));
        centerNode.setStroke(Color.WHITE);
        centerNode.setStrokeWidth(2.5);
        centerNode.setEffect(new DropShadow(20, Color.web("#0094FF")));

        Text centerLabel = new Text(centerX - pdfName.length() * 3.5, centerY + 5, pdfName);
        centerLabel.setFill(Color.WHITE);
        centerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        graphGroup.getChildren().addAll(centerNode, centerLabel);

        double radius = Math.min(width, height) / 2.3;
        List<String> words = new ArrayList<>(keywords);
        Random rand = new Random();

        for (int i = 0; i < words.size(); i++) {
            double angle = 2 * Math.PI * i / words.size();
            double x = centerX + radius * Math.cos(angle);
            double y = centerY + radius * Math.sin(angle);

            Circle node = new Circle(centerX, centerY, 0, Color.web("#3C8DFF"));
            node.setStroke(Color.web("#DDEBFF"));
            node.setStrokeWidth(1.3);
            Tooltip.install(node, new Tooltip("Keyword: " + words.get(i)));

            Line edge = new Line(centerX, centerY, centerX, centerY);
            edge.setStroke(Color.web("#6FBFFF"));
            edge.setOpacity(0.4);

            Text label = new Text(words.get(i));
            label.setFill(Color.web("#E6F0FF"));
            label.setStyle("-fx-font-size: 12px;");
            label.setX(x - words.get(i).length() * 3);
            label.setY(y + 4);

            node.setOnMouseEntered(e -> node.setEffect(new DropShadow(15, Color.web("#00FFFF"))));
            node.setOnMouseExited(e -> node.setEffect(null));

            Timeline anim = new Timeline(
                    new KeyFrame(Duration.millis(300 + rand.nextInt(500)),
                            new KeyValue(node.centerXProperty(), x, Interpolator.EASE_OUT),
                            new KeyValue(node.centerYProperty(), y, Interpolator.EASE_OUT),
                            new KeyValue(node.radiusProperty(), 18, Interpolator.EASE_OUT),
                            new KeyValue(edge.endXProperty(), x, Interpolator.EASE_OUT),
                            new KeyValue(edge.endYProperty(), y, Interpolator.EASE_OUT))
            );
            anim.play();

            graphGroup.getChildren().addAll(edge, node, label);
        }
    }

    // ===========================================================
    // MULTI-PDF GRAPH with FORCE-DIRECTED LAYOUT (physics)
    // ===========================================================
    private void generateConnectionGraph(Map<String, Set<String>> map) {
        graphGroup.getChildren().clear();
        physNodes.clear();
        physEdges.clear();

        int n = map.size();
        if (n == 0) return;

        double width = graphPane.getWidth() > 0 ? graphPane.getWidth() : 900;
        double height = graphPane.getHeight() > 0 ? graphPane.getHeight() : 600;
        double centerX = width / 2, centerY = height / 2;
        double startRadius = Math.min(centerX, centerY) - 120;

        // Nodes (init in a circle)
        List<String> files = new ArrayList<>(map.keySet());
        nodeMap.clear();

        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n;
            double x = centerX + startRadius * Math.cos(angle);
            double y = centerY + startRadius * Math.sin(angle);

            Circle node = new Circle(x, y, 40, Color.web("#3C8DFF"));
            node.setStroke(Color.WHITE);
            node.setStrokeWidth(1.5);
            node.setEffect(new DropShadow(10, Color.web("#0078FF")));

            String fileName = files.get(i);
            Tooltip.install(node, new Tooltip(fileName));
            nodeMap.put(fileName, node);

            NodeBody body = new NodeBody(fileName, node);
            physNodes.put(fileName, body);

            node.setOnMouseClicked(e -> highlightPDFSection(fileName));

            Text label = new Text(x - fileName.length() * 3, y + 4, fileName);
            label.setFill(Color.web("#E6F0FF"));
            label.setStyle("-fx-font-size: 12px; -fx-font-weight: 600;");

            // keep label following node while physics runs
            node.centerXProperty().addListener((obs, ov, nv) -> label.setX(nv.doubleValue() - fileName.length() * 3));
            node.centerYProperty().addListener((obs, ov, nv) -> label.setY(nv.doubleValue() + 4));

            graphGroup.getChildren().addAll(node, label);
        }

        // Edges with weight-based springs
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                String A = files.get(i), B = files.get(j);
                Set<String> common = new HashSet<>(map.get(A));
                common.retainAll(map.get(B));

                if (!common.isEmpty()) {
                    Circle ca = nodeMap.get(A);
                    Circle cb = nodeMap.get(B);
                    Line edge = new Line(ca.getCenterX(), ca.getCenterY(), cb.getCenterX(), cb.getCenterY());

                    double weight = Math.min(common.size(), 10);
                    edge.setStroke(Color.web("#6FBFFF"));
                    edge.setOpacity(0.3 + weight * 0.05);
                    edge.setStrokeWidth(1.1 + weight * 0.15);
                    Tooltip.install(edge, new Tooltip("Shared: " + String.join(", ", common)));

                    edge.setOnMouseClicked(e -> {
                        glowConnection(ca, cb);
                        showSharedKeywords(A, B, common);
                    });

                    graphGroup.getChildren().add(0, edge);

                    NodeBody a = physNodes.get(A);
                    NodeBody b = physNodes.get(B);

                    double rest = minRestLen + (maxRestLen - minRestLen) * (1.0 - weight / 10.0);
                    double k = springBaseK * (0.7 + 0.3 * weight);
                    physEdges.add(new EdgeBody(a, b, edge, k, rest));
                }
            }
        }

        // Start physics
        if (physicsEnabled) startForceSimulation();
    }

    // ===========================================================
    // Physics engine
    // ===========================================================
    private void startForceSimulation() {
        stopForceSimulation();
        forceTimeline = new Timeline(new KeyFrame(Duration.seconds(timeStep), e -> stepForces()));
        forceTimeline.setCycleCount(Animation.INDEFINITE);
        forceTimeline.play();
    }

    private void stopForceSimulation() {
        if (forceTimeline != null) {
            forceTimeline.stop();
            forceTimeline = null;
        }
    }

    @FXML
    private void togglePhysics() {
        physicsEnabled = !physicsEnabled;
        if (physicsEnabled) startForceSimulation();
        else stopForceSimulation();
    }

    private void stepForces() {
        if (physNodes.isEmpty()) return;

        // 1) Repulsion (all-pairs)
        List<NodeBody> nodes = new ArrayList<>(physNodes.values());
        for (int i = 0; i < nodes.size(); i++) {
            NodeBody ni = nodes.get(i);
            double fx = 0, fy = 0;

            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;
                NodeBody nj = nodes.get(j);

                double dx = ni.x() - nj.x();
                double dy = ni.y() - nj.y();
                double dist2 = dx * dx + dy * dy + 0.01; // avoid div-by-zero
                double dist = Math.sqrt(dist2);

                // Coulomb repulsion: F = k / r^2
                double force = repulsionK / dist2;
                force = Math.min(force, repelClamp);

                fx += force * (dx / dist);
                fy += force * (dy / dist);
            }

            // Euler integrate velocities with damping
            ni.vx = (ni.vx + (fx / ni.mass) * timeStep) * damping;
            ni.vy = (ni.vy + (fy / ni.mass) * timeStep) * damping;
        }

        // 2) Springs (edges)
        for (EdgeBody e : physEdges) {
            NodeBody a = e.a, b = e.b;
            double dx = b.x() - a.x();
            double dy = b.y() - a.y();
            double dist = Math.sqrt(dx * dx + dy * dy) + 0.001;
            double stretch = dist - e.restLen;

            // Hooke: F = k * stretch along the direction
            double fx = e.k * stretch * (dx / dist);
            double fy = e.k * stretch * (dy / dist);

            // Apply equal and opposite forces
            a.vx += fx * timeStep;
            a.vy += fy * timeStep;
            b.vx -= fx * timeStep;
            b.vy -= fy * timeStep;
        }

        // 3) Integrate positions + soft bounds
        double pad = 80;
        double w = Math.max(graphPane.getWidth(), 600);
        double h = Math.max(graphPane.getHeight(), 400);

        for (NodeBody n : physNodes.values()) {
            // clamp speed
            double speed = Math.sqrt(n.vx * n.vx + n.vy * n.vy);
            if (speed > maxSpeed) {
                n.vx = n.vx / speed * maxSpeed;
                n.vy = n.vy / speed * maxSpeed;
            }

            double nx = n.x() + n.vx * timeStep;
            double ny = n.y() + n.vy * timeStep;

            // soft bounds with bounce
            if (nx < pad) { nx = pad; n.vx *= -0.4; }
            if (nx > w - pad) { nx = w - pad; n.vx *= -0.4; }
            if (ny < pad) { ny = pad; n.vy *= -0.4; }
            if (ny > h - pad) { ny = h - pad; n.vy *= -0.4; }

            n.set(nx, ny);
        }

        // 4) Update edge endpoints
        for (EdgeBody e : physEdges) {
            e.line.setStartX(e.a.x());
            e.line.setStartY(e.a.y());
            e.line.setEndX(e.b.x());
            e.line.setEndY(e.b.y());
        }
    }

    // ===========================================================
    // Inspector / highlighting / glow
    // ===========================================================
    private void showSharedKeywords(String pdfA, String pdfB, Set<String> shared) {
        if (connectionInspector != null) {
            connectionInspector.setVisible(true);
            connectionInspector.setTranslateX(300);

            inspectorTitle.setText("🔗 " + pdfA + " ↔ " + pdfB);
            inspectorSubtitle.setText(shared.size() + " shared keyword(s):");
            sharedKeywordList.getItems().setAll(shared.stream().sorted().toList());

            TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), connectionInspector);
            slideIn.setToX(0);
            slideIn.play();
        } else {
            new Alert(Alert.AlertType.INFORMATION,
                    "Shared Keywords between " + pdfA + " and " + pdfB + ":\n" + String.join(", ", shared))
                    .showAndWait();
        }
    }

    private void glowConnection(Circle a, Circle b) {
        DropShadow glow = new DropShadow(25, Color.web("#00FFFF"));
        a.setEffect(glow);
        b.setEffect(glow);
    }

    private void highlightPDFSection(String fileName) {
        String text = textAreaContent.getText();
        int index = text.indexOf("=== " + fileName + " ===");
        if (index >= 0) {
            textAreaContent.positionCaret(index);
            textAreaContent.selectRange(index, Math.min(text.length(), index + fileName.length() + 20));
            textAreaContent.setStyle("""
                -fx-highlight-fill: rgba(90,150,255,0.5);
                -fx-highlight-text-fill: black;
                -fx-control-inner-background: #1a1f2b;
                -fx-text-fill: #f2f2f2;
            """);
        }
    }

    // ===========================================================
    // TOOLBAR + EDITOR ACTIONS
    // ===========================================================
    @FXML private void onClearClicked() {
        textAreaContent.clear();
        graphGroup.getChildren().clear();
        stopForceSimulation();
    }
    @FXML private void onCutClicked() { textAreaContent.cut(); }
    @FXML private void onCopyClicked() { textAreaContent.copy(); }
    @FXML private void onPasteClicked() { textAreaContent.paste(); }
    @FXML private void onUndoClicked() { textAreaContent.undo(); }
    @FXML private void onRedoClicked() { textAreaContent.redo(); }

    @FXML
    private void onFindClicked(ActionEvent event) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("🔍 Find Text");
        dialog.setHeaderText("Search in Text Area");
        dialog.setContentText("Enter keyword:");
        dialog.showAndWait().ifPresent(keyword -> {
            String content = textAreaContent.getText().toLowerCase();
            int count = content.split(keyword.toLowerCase(), -1).length - 1;
            new Alert(Alert.AlertType.INFORMATION,
                    "Found " + count + " occurrence(s) of \"" + keyword + "\"").showAndWait();
        });
    }

    @FXML
    private void onHighlightClicked() {
        String selected = textAreaContent.getSelectedText();
        if (selected == null || selected.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "Please select text to highlight.").showAndWait();
            return;
        }
        textAreaContent.setStyle("""
            -fx-control-inner-background: #0d1117;
            -fx-text-fill: #f2f2f2;
            -fx-highlight-fill: rgba(255,255,100,0.4);
            -fx-highlight-text-fill: black;
        """);
        new Alert(Alert.AlertType.INFORMATION, "✨ Highlighted selected text!").showAndWait();
    }

    @FXML
    private void onSaveClicked() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setInitialFileName("output.txt");
            File file = fileChooser.showSaveDialog(null);
            if (file != null)
                java.nio.file.Files.writeString(file.toPath(), textAreaContent.getText());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void openKeywordChart() {
        if (keywordFrequency.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "📊 No keyword data available!").showAndWait();
            return;
        }
        KeywordFrequencyChart.showChart(keywordFrequency);
    }

    @FXML
    private void openNotes() {
        try {
            URL resource = getClass().getResource("/fxml/NoteEditor.fxml");
            if (resource == null)
                resource = getClass().getResource("/com/detextion/notes/NoteEditor.fxml");

            if (resource == null) {
                new Alert(Alert.AlertType.ERROR, "❌ NoteEditor.fxml not found.").showAndWait();
                return;
            }

            FXMLLoader loader = new FXMLLoader(resource);
            Stage stage = new Stage();
            stage.setTitle("🗒 Note Editor");
            stage.setScene(new Scene(loader.load()));
            stage.setResizable(true);
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "⚠ Failed to open Note Editor:\n" + e.getMessage()).showAndWait();
        }
    }
    // ===========================================================
    // === NEW SECTION: AI-ASSISTED TEXT INSIGHTS ===
    // ===========================================================

    @FXML
    private void onSummarizeText() {
        String content = textAreaContent.getSelectedText();
        if (content.isEmpty()) content = textAreaContent.getText();

        if (content.isBlank()) {
            new Alert(Alert.AlertType.INFORMATION, "No text available to summarize.").showAndWait();
            return;
        }

        String summary = generateSummary(content);
        TextArea summaryArea = new TextArea(summary);
        summaryArea.setWrapText(true);
        summaryArea.setEditable(false);
        summaryArea.setPrefHeight(300);

        Alert summaryAlert = new Alert(Alert.AlertType.INFORMATION);
        summaryAlert.setTitle("🧠 AI Summary");
        summaryAlert.setHeaderText("Extractive Summary:");
        summaryAlert.getDialogPane().setContent(summaryArea);
        summaryAlert.showAndWait();
    }

    private String generateSummary(String text) {
        String[] sentences = text.split("(?<=[.!?])\\s+");
        if (sentences.length <= 3) return text;

        Map<String, Integer> freq = new HashMap<>();
        String[] words = text.toLowerCase().split("\\W+");
        for (String w : words) {
            if (w.length() < 4) continue;
            freq.put(w, freq.getOrDefault(w, 0) + 1);
        }

        Map<String, Double> scores = new HashMap<>();
        for (String s : sentences) {
            double score = 0;
            for (String w : s.toLowerCase().split("\\W+"))
                score += freq.getOrDefault(w, 0);
            scores.put(s, score);
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(" "));
    }

    @FXML
    private void onComparePDFs() {
        if (pdfKeywords.size() < 2) {
            new Alert(Alert.AlertType.INFORMATION, "Load at least two PDFs to compare.").showAndWait();
            return;
        }

        List<String> files = new ArrayList<>(pdfKeywords.keySet());
        ChoiceDialog<String> dialogA = new ChoiceDialog<>(files.get(0), files);
        dialogA.setTitle("Select PDF A");
        dialogA.setHeaderText("Choose the first PDF to compare:");
        Optional<String> pdfAOpt = dialogA.showAndWait();
        if (pdfAOpt.isEmpty()) return;

        ChoiceDialog<String> dialogB = new ChoiceDialog<>(files.get(1), files);
        dialogB.setTitle("Select PDF B");
        dialogB.setHeaderText("Choose the second PDF to compare:");
        Optional<String> pdfBOpt = dialogB.showAndWait();
        if (pdfBOpt.isEmpty()) return;

        String pdfA = pdfAOpt.get();
        String pdfB = pdfBOpt.get();
        Set<String> setA = pdfKeywords.get(pdfA);
        Set<String> setB = pdfKeywords.get(pdfB);

        Set<String> shared = new HashSet<>(setA);
        shared.retainAll(setB);

        double similarity = 100.0 * shared.size() / Math.sqrt(setA.size() * setB.size());

        String result = String.format("📄 %s ↔ %s\nSimilarity: %.2f%%\n\nShared Keywords:\n%s",
                pdfA, pdfB, similarity, String.join(", ", shared));

        TextArea area = new TextArea(result);
        area.setWrapText(true);
        area.setEditable(false);
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("📊 PDF Similarity");
        alert.getDialogPane().setContent(area);
        alert.showAndWait();
    }

    @FXML
    private void onClusterKeywords() {
        if (keywordFrequency.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "No keywords loaded yet!").showAndWait();
            return;
        }

        List<String> words = new ArrayList<>(keywordFrequency.keySet());
        Map<String, List<String>> clusters = new LinkedHashMap<>();

        for (String word : words) {
            boolean added = false;
            for (String key : clusters.keySet()) {
                if (areSimilar(word, key)) {
                    clusters.get(key).add(word);
                    added = true;
                    break;
                }
            }
            if (!added) clusters.put(word, new ArrayList<>(List.of(word)));
        }

        StringBuilder sb = new StringBuilder("🔠 Keyword Clusters:\n\n");
        for (var entry : clusters.entrySet()) {
            sb.append("• ").append(entry.getKey()).append(" → ").append(entry.getValue()).append("\n");
        }

        TextArea area = new TextArea(sb.toString());
        area.setEditable(false);
        area.setWrapText(true);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("🤖 Keyword Clustering");
        alert.getDialogPane().setContent(area);
        alert.showAndWait();
    }

    private boolean areSimilar(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 3) return false;
        int commonPrefix = 0;
        for (int i = 0; i < Math.min(a.length(), b.length()); i++) {
            if (a.charAt(i) == b.charAt(i)) commonPrefix++;
            else break;
        }
        return (commonPrefix >= Math.min(a.length(), b.length()) / 2);
    }

}
