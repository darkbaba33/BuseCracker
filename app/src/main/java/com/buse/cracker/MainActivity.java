package com.buse.cracker;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus, tvResults;
    private String[] keywords = {"http://", "https://", "auth", "key", "license", "hwid", "verify", "invalid", "t.me", "api.", "expire", "success", "login", "void"};
    private List<String> urlsFound = new ArrayList<>();

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        analyzeFile(uri);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        tvResults = findViewById(R.id.tv_results);
        Button btnSelect = findViewById(R.id.btn_select_file);

        btnSelect.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            filePickerLauncher.launch(intent);
        });
    }

    private void analyzeFile(Uri uri) {
        tvStatus.setText("Durum: Dosya okunuyor...");
        tvResults.setText("");
        urlsFound.clear();

        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int nRead;
                byte[] data = new byte[16384];
                while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                byte[] fileBytes = buffer.toByteArray();
                inputStream.close();

                StringBuilder sb = new StringBuilder();
                sb.append("--- ANALIZ SONUCLARI ---\n\n");

                String text = new String(fileBytes, "ISO-8859-1");
                
                sb.append("[+] BULUNAN WEB LINKLERI:\n");
                for (String kw : keywords) {
                    if (!kw.startsWith("http")) continue;
                    int index = 0;
                    while ((index = text.toLowerCase().indexOf(kw, index)) != -1) {
                        int end = index;
                        while (end < text.length() && isPrintable(text.charAt(end))) {
                            end++;
                        }
                        String found = text.substring(index, end);
                        if (found.length() > 5 && found.length() < 100) {
                            if (!urlsFound.contains(found)) {
                                urlsFound.add(found);
                                sb.append(" -> ").append(found).append("\n");
                            }
                        }
                        index += kw.length();
                    }
                }

                sb.append("\n[+] BULUNAN ANAHTAR KELIMELER:\n");
                for (String kw : keywords) {
                    if (kw.startsWith("http")) continue;
                    int index = 0;
                    while ((index = text.toLowerCase().indexOf(kw, index)) != -1) {
                        int start = Math.max(0, index - 15);
                        int end = Math.min(text.length(), index + 35);
                        String found = text.substring(start, end).replaceAll("[^\\x20-\\x7E]", ".");
                        sb.append(" -> [").append(kw).append("]: ").append(found).append("\n");
                        index += kw.length();
                        if (sb.length() > 8000) break;
                    }
                }

                runOnUiThread(() -> {
                    tvStatus.setText("Durum: Analiz tamamlandi!");
                    tvResults.setText(sb.toString());
                });

            } catch (Exception e) {
                runOnUiThread(() -> tvStatus.setText("Hata: " + e.getMessage()));
            }
        }).start();
    }

    private boolean isPrintable(char c) {
        return (c >= 32 && c <= 126) || c == '\n';
    }
}
