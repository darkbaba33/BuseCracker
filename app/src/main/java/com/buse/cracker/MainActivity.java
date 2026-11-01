package com.buse.cracker;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus, tvResults;
    private ProgressBar progressBar;
    private String[] keywords = {"auth", "key", "license", "hwid", "verify", "invalid", "t.me", "api.", "expire", "login", "void", "token", "panel"};
    private List<String> urlsFound = new ArrayList<>();
    private static final int PICK_FILE_REQUEST = 100;

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
            startActivityForResult(intent, PICK_FILE_REQUEST);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                analyzeFile(uri);
            }
        }
    }

    private void analyzeFile(Uri uri) {
        tvStatus.setText("> Durum: Dosya okunuyor...");
        tvResults.setText("");
        urlsFound.clear();
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream == null) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Dosya acilamadi!", Toast.LENGTH_SHORT).show());
                    return;
                }
                
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
                String title1 = "[+] TESPIT EDILEN LINKLER:\n";
                int start1 = sb.length();
                sb.append(title1);
                sb.setSpan(new ForegroundColorSpan(Color.parseColor("#FFCC00")), start1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                
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
                            int startUrl = sb.length();
                            sb.append(urlStr);
                            sb.setSpan(new ForegroundColorSpan(Color.parseColor("#00FFCC")), startUrl, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }
                    index += 4;
                }

                // KELİME ARAMA
                String title2 = "\n[+] SIFRE / GIZLI ANAHTARLAR:\n";
                int start2 = sb.length();
                sb.append(title2);
                sb.setSpan(new ForegroundColorSpan(Color.parseColor("#FFCC00")), start2, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                for (String kw : keywords) {
                    int kwIndex = 0;
                    while ((kwIndex = text.toLowerCase().indexOf(kw, kwIndex)) != -1) {
                        int start = Math.max(0, kwIndex - 15);
                        int end = Math.min(text.length(), kwIndex + 35);
                        String found = text.substring(start, end).replaceAll("[^\\x20-\\x7E]", ".");
                        String lineStr = " -> [" + kw.toUpperCase() + "]: " + found + "\n";
                        int startLine = sb.length();
                        sb.append(lineStr);
                        sb.setSpan(new ForegroundColorSpan(Color.parseColor("#FF0055")), startLine, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        
                        kwIndex += kw.length();
                        if (sb.length() > 15000) break;
                    }
                }

                runOnUiThread(() -> {
                    tvStatus.setText("> Durum: Analiz Tamamlandi!");
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

    private boolean isPrintable(char c) {
        return (c >= 32 && c <= 126) || c == '\n' || c == '\r';
    }
}
