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
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Properties;
import java.util.ArrayList;

public class TranslatorApp {
    private JFrame frame;
    private JTextField inputFolderField;
    private JTextField outputFolderField;
    private JComboBox<String> languageComboBox;
    private JTextArea logArea; // Area for displaying process logs

    public TranslatorApp() {
        // Build the GUI interface
        frame = new JFrame("I-Trans 1.0");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400); // Window size
        frame.setLayout(new FlowLayout()); // Using FlowLayout

        // Center the window on the screen
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

        // Add components to the frame
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
            
                    StringBuilder modifiedContent = new StringBuilder(content);
            
                    // List of excluded words
                    List<String> excludedWords = new ArrayList<>();
            
                    // List of replaced words
                    List<String> replacedWords = new ArrayList<>();

                 // Load configuration from config.ini
                 Properties properties = new Properties();
                 try {
                    String jarPath = new File(TranslatorApp.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent();
                    File configFile = new File(jarPath, "config.ini");
                    try (Reader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
                        properties.load(reader);
                    }
                    String excluded = properties.getProperty("excludedWords", "");
                    String replaced = properties.getProperty("replacedWords", "");
                    for (String word : excluded.split(",")) {
                        excludedWords.add(word.trim());
                    }
                    for (String word : replaced.split(",")) {
                        replacedWords.add(word.trim());
                    }
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(null, "Failed to load configuration: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
        
                    while (matcher.find()) {
                        String originalText = matcher.group(1).trim();
                        publish("Original text: " + originalText); // Log found text
            
                        for (int i = 0; i < excludedWords.size(); i++) {
                            String excludedWord = excludedWords.get(i);
                            String wordPattern = "\\b" + Pattern.quote(excludedWord) + "\\b";
                            Pattern wordRegex = Pattern.compile(wordPattern);
                            Matcher wordMatcher = wordRegex.matcher(originalText);
            
                            if (wordMatcher.find()) {
                                String uniquePlaceholder = "[" + (i + 1) + "[" + excludedWord + "]]";
                                originalText = wordMatcher.replaceAll(uniquePlaceholder);
                            }
                        }
            
                        String translatedText = translateText(originalText, targetLanguage);
                        //publish("Translated text: " + translatedText); // Log translated text
                        
                        String tagPattern = "\\[(\\d+)\\[(.*?)]]";
                        Pattern tagRegex = Pattern.compile(tagPattern);
                        Matcher tagMatcher = tagRegex.matcher(translatedText);
                        
                        while (tagMatcher.find()) {
                            int index = Integer.parseInt(tagMatcher.group(1)) - 1;
                            
                            if (index >= 0 && index < replacedWords.size()) {
                                String originalWord = replacedWords.get(index);
                                translatedText = translatedText.replace(tagMatcher.group(0), originalWord);
                            } else {
                                publish("Index out of bounds: " + index); // Log out bounds id
                            }
                        }
                        
                        // Remove all unused words that have the format [[word]] and word]]
                        translatedText = translatedText.replaceAll("\\[\\[.*?\\]\\]", ""); // Remove [[...]] along with its contents
                        translatedText = translatedText.replaceAll("\\b[^\\s\\[]*\\]\\]", ""); // Remove words ending with ]]
                        publish("Final translated text: " + translatedText + "\n"); // Log final result
                        modifiedContent = new StringBuilder(modifiedContent.toString().replace(matcher.group(0), "String=\"" + translatedText + "\""));
                    }
            
                    Files.write(outputFile.toPath(), modifiedContent.toString().getBytes("UTF-16LE"));
                    publish("File saved: " + outputFile.getAbsolutePath() + "\n");
                } catch (IOException e) {
                    publish("Error processing file: " + inputFile.getAbsolutePath() + " - " + e.getMessage());
                }
            }            
            
            private String readFileWithBOM(File file) throws IOException {
                // Read file content, taking into account BOM for UTF-8 and UTF-16
                byte[] bytes = Files.readAllBytes(file.toPath());
                String content = new String(bytes, "UTF-16LE"); // Assuming the file is in UTF-16LE
                return content;
            }

            private String translateText(String text, String targetLanguage) {
                try {
                    // Temporarily replace \r and \n with placeholders
                    text = text.replace("\\r", " [[CR]] ").replace("\\n", " [[LF]] ");

                    // Create a URL for the Google Translate API
                    String urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=" + targetLanguage + "&dt=t&q=" + java.net.URLEncoder.encode(text, "UTF-8");
                    // Use URI to resolve URL issues
                    URI uri = new URI(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Accept-Charset", "UTF-8");

                    // Read the response
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder response = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    // Extract translation from JSON response
                    // String jsonResponse = response.toString();
                    // String translatedText = jsonResponse.split("\"")[1]; // Get the translation
                    
                    // Extract translation text from JSON response
                    String jsonResponse = response.toString();

                    // Use regex to safely extract the translation from JSON
                    String translatedText = jsonResponse.replaceAll(".*?\"(.*?)\".*", "$1");

                    // Return placeholders to escape sequences
                    translatedText = translatedText.replace("[[CR]]", "\\r").replace("[[LF]]", "\\n");

                    return translatedText;
                } catch (Exception e) {
                    publish("Translation error: " + e.getMessage());
                    return text; // Return the original text on error
                }
            }
        };

        worker.execute(); // Execute the worker
    }

    public static void main(String[] args) {
        new TranslatorApp(); // Create and run the application
    }
}
