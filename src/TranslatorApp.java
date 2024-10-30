import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TranslatorApp {
    private JFrame frame;
    private JTextField inputFolderField;
    private JTextField outputFolderField;
    private JComboBox<String> languageComboBox;
    private JTextArea logArea; // Area for displaying process logs

    public TranslatorApp() {
        // Build GUI interface
        frame = new JFrame("I-Trans 1.0");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400); // Window size
        frame.setLayout(new FlowLayout()); // Using FlowLayout

        // Center window on the screen
        frame.setLocationRelativeTo(null);

        // Change window icon (replace "path/to/icon.png" with your icon path)
        frame.setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getClassLoader().getResource("icon.png")));

        // Create JTextFields for input and output folder selection
        inputFolderField = new JTextField(30); // Larger size
        outputFolderField = new JTextField(30); // Larger size

        JButton inputButton = new JButton("Select Input Folder");
        inputButton.addActionListener(e -> chooseFolder(inputFolderField));

        JButton outputButton = new JButton("Select Output Folder");
        outputButton.addActionListener(e -> chooseFolder(outputFolderField));

        JButton translateButton = new JButton("Translate");
        translateButton.addActionListener(e -> translateFiles());

        // Dropdown for language selection
        String[] languages = {"id", "am", "ar", "eu", "bn", "en-GB", "pt-BR", "bg", "ca", "chr", "jam", "cs", "da", "nl", "en", "et", "fil", "fi", "fr", "de", "el", "gu", "iw", "hi", "hu", "it", "ja", "kn", "ko", "lv", "lt", "ms", "ml", "mr", "no", "pl", "pt-PT", "ro", "ru", "sr", "zh-CN", "sk", "sl", "es", "sw", "sv", "ta", "te", "th", "zh-TW", "tr", "ur", "uk", "vi", "cy"};
        languageComboBox = new JComboBox<>(languages);

        // Adding components to the frame
        frame.add(new JLabel("Input Folder:"));
        frame.add(inputFolderField);
        frame.add(inputButton);

        frame.add(new JLabel("Output Folder:"));
        frame.add(outputFolderField);
        frame.add(outputButton);

        frame.add(new JLabel("Select Language:"));
        frame.add(languageComboBox);
        frame.add(translateButton);

        // Create JTextArea for process logs
        logArea = new JTextArea(15, 50); // Log area size
        logArea.setEditable(false); // Make it read-only
        JScrollPane scrollPane = new JScrollPane(logArea); // Add scroll to log area
        frame.add(scrollPane);

        frame.setVisible(true);
    }

    private void chooseFolder(JTextField textField) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            textField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void translateFiles() {
        String inputFolder = inputFolderField.getText();
        String outputFolder = outputFolderField.getText();
        String targetLanguage = (String) languageComboBox.getSelectedItem();

        if (inputFolder.isEmpty() || outputFolder.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Please select input and output folders.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Use SwingWorker to process translation in the background
        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    Files.walk(Paths.get(inputFolder))
                            .filter(path -> path.toString().endsWith(".xml"))
                            .forEach(path -> {
                                // Get relative path from inputFolder to maintain folder structure
                                Path relativePath = Paths.get(inputFolder).relativize(path);
                                File outputFile = new File(outputFolder, relativePath.toString());

                                // Create output folder if it doesn't exist
                                outputFile.getParentFile().mkdirs();

                                // Translate the file
                                translateFile(path.toFile(), outputFile, targetLanguage);
                            });
                } catch (IOException e) {
                    publish("An error occurred while processing files: " + e.getMessage());
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    logArea.append(message + "\n");
                    logArea.setCaretPosition(logArea.getDocument().getLength()); // Scroll to bottom
                }
            }

            @Override
            protected void done() {
                try {
                    get(); // Catch any exceptions from doInBackground
                } catch (Exception e) {
                    publish("Translation failed: " + e.getMessage());
                }
                JOptionPane.showMessageDialog(frame, "Translation process completed.", "Success", JOptionPane.INFORMATION_MESSAGE);
            }

            private void translateFile(File inputFile, File outputFile, String targetLanguage) {
                try {
                    String content = readFileWithBOM(inputFile);
                    String regex = "String=\"(.*?)\"";
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(content);

                    // Replacing the matched strings with their translations
                    while (matcher.find()) {
                        String originalText = matcher.group(1).trim(); // Get the string inside quotes
                        String translatedText = translateText(originalText, targetLanguage);
                        content = content.replace(matcher.group(0), "String=\"" + translatedText + "\"");
                        logTranslation(inputFile, originalText, translatedText); // Log translation process
                    }

                    // Save the translated file
                    Files.write(outputFile.toPath(), content.getBytes("UTF-16LE"));
                    // Log after file successfully saved
                    publish("File saved: " + outputFile.getAbsolutePath());
                } catch (IOException e) {
                    publish("Error processing file: " + inputFile.getAbsolutePath() + " - " + e.getMessage());
                }
            }

            private void logTranslation(File inputFile, String originalText, String translatedText) {
                // Add log to JTextArea
                publish("Translated from file: " + inputFile.getAbsolutePath());
                publish("Original: " + originalText);
                publish("Translated: " + translatedText + "\n");
            }

            private String readFileWithBOM(File file) throws IOException {
                // Read file content, taking into account BOM for UTF-8 and UTF-16
                byte[] bytes = Files.readAllBytes(file.toPath());
                String content = new String(bytes, "UTF-16LE"); // Assuming the file is in UTF-16LE
                return content;
            }

            private String translateText(String text, String targetLanguage) {
                try {
                    // Create a URL for the Google Translate API
                    String urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=" + targetLanguage + "&dt=t&q=" + java.net.URLEncoder.encode(text, "UTF-8");
                    // Use URI to resolve URL issues
                    URI uri = new URI(urlStr);
                    URL url = uri.toURL(); // Convert URI to URL

                    // Opens an HTTP connection
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                    // Read results from the API
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String inputLine;
                    StringBuilder response = new StringBuilder();

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    // Parsing JSON response (results from Google Translate API)
                    String jsonResponse = response.toString();
                    // The translation results will be in the first array of JSON
                    String translatedText = jsonResponse.split("\"")[1];

                    return translatedText; // Return the translated text
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(frame, "Failed to translate text.", "Error", JOptionPane.ERROR_MESSAGE);
                    return text; // Return original text if translation fails
                }
            }
        };

        worker.execute(); // Execute worker
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(TranslatorApp::new);
    }
}
