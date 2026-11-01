package com.buse.cracker;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus, tvResults;
    private LinearProgressIndicator progressBar;
    private String[] keywords = {"auth", "key", "license", "hwid", "verify", "invalid", "t.me", "api.", "expire", "login", "void", "token", "panel"};
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
        progressBar = findViewById(R.id.progressBar);
        Button btnSelect = findViewById(R.id.btn_select_file);

        btnSelect.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            filePickerLauncher.launch(intent);
        });
    }

    private void analyzeFile(Uri uri) {
        tvStatus.setText("> Durum: Dosya okunuyor ve bellek taraniyor...");
        tvResults.setText("");
        urlsFound.clear();
        progressBar.setVisibility(View.VISIBLE);

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

                SpannableStringBuilder sb = new SpannableStringBuilder();
                sb.append("================================\n");
                sb.append("      BUSE CRACKER RAPORU       \n");
                sb.append("================================\n\n");

                String text = new String(fileBytes, "ISO-8859-1");
                
                // LINK ARAMA
                sb.append("[+] TESPIT EDILEN LINKLER:\n");
                applyColor(sb, "[+] TESPIT EDILEN LINKLER:\n", Color.parseColor("#FFCC00"));
                
                int index = 0;
                while ((index = text.toLowerCase().indexOf("http", index)) != -1) {
                    int end = index;
                    while (end < text.length() && isPrintable(text.charAt(end))) {
                        end++;
                    }
                    String found = text.substring(index, end);
                    if (found.length() > 5 && found.length() < 100 && (found.startsWith("http://") || found.startsWith("https://"))) {
                        if (!urlsFound.contains(found)) {
                            urlsFound.add(found);
                            String urlStr = " -> " + found + "\n";
                            sb.append(urlStr);
                            applyColor(sb, urlStr, Color.parseColor("#00FFCC")); // Neon Mavi
                        }
                    }
                    index += 4;
                }

                // KELİME ARAMA
                sb.append("\n[+] SIFRE / GIZLI ANAHTARLAR:\n");
                applyColor(sb, "\n[+] SIFRE / GIZLI ANAHTARLAR:\n", Color.parseColor("#FFCC00"));

                for (String kw : keywords) {
                    int kwIndex = 0;
                    while ((kwIndex = text.toLowerCase().indexOf(kw, kwIndex)) != -1) {
                        int start = Math.max(0, kwIndex - 15);
                        int end = Math.min(text.length(), kwIndex + 35);
                        String found = text.substring(start, end).replaceAll("[^\\x20-\\x7E]", ".");
                        String lineStr = " -> [" + kw.toUpperCase() + "]: " + found + "\n";
                        sb.append(lineStr);
                        applyColor(sb, lineStr, Color.parseColor("#FF0055")); // Neon Kırmızı
                        kwIndex += kw.length();
                        if (sb.length() > 10000) break;
                    }
                }

                runOnUiThread(() -> {
                    tvStatus.setText("> Durum: Analiz Basariyla Tamamlandi!");
                    progressBar.setVisibility(View.INVISIBLE);
                    tvResults.setText(sb);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvStatus.setText("> Hata: " + e.getMessage());
                    progressBar.setVisibility(View.INVISIBLE);
                });
            }
        }).start();
    }

    private void applyColor(SpannableStringBuilder sb, String text, int color) {
        int start = sb.length() - text.length();
        sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (text.contains("HTTP") || text.contains("->")) {
            sb.setSpan(new TypefaceSpan("monospace"), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private boolean isPrintable(char c) {
        return (c >= 32 && c <= 126) || c == '\n' || c == '\r';
    }
}
