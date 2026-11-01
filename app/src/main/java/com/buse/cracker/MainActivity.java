package com.buse.cracker;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus, tvResults;
    private ProgressBar progressBar;
    private Button btnSelect;
    
    // Analiz edilecek gizli anahtar kelimeler
    private final String[] KEYWORDS = {"auth", "key", "license", "hwid", "verify", "invalid", "t.me", "api.", "expire", "login", "void", "token", "panel", "http", "https"};
    private static final int MAX_RESULTS = 200; // Ekranın donmasını engellemek için limit
    private static final int PICK_FILE_REQUEST = 100;
    
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        tvResults = findViewById(R.id.tv_results);
        progressBar = findViewById(R.id.progressBar);
        btnSelect = findViewById(R.id.btn_select_file);

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
        tvStatus.setText("> Durum: Dosya hazirlaniyor...");
        tvResults.setText("");
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
        btnSelect.setEnabled(false);

        new Thread(() -> {
            long fileSize = 0;
            String fileName = "Bilinmeyen Dosya";

            try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) {
                if (pfd != null) fileSize = pfd.getStatSize();
            } catch (Exception ignored) {}

            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) fileName = cursor.getString(nameIndex);
                }
            } catch (Exception ignored) {}

            final String fName = fileName;
            mainHandler.post(() -> tvStatus.setText("> Analiz Ediliyor: " + fName));

            SpannableStringBuilder finalResults = new SpannableStringBuilder();
            Set<String> uniqueFound = new HashSet<>();
            int totalFound = 0;

            appendColoredText(finalResults, "================================\n", Color.parseColor("#00FFCC"));
            appendColoredText(finalResults, "      BUSE CRACKER RAPORU       \n", Color.parseColor("#00FFCC"));
            appendColoredText(finalResults, "================================\n\n", Color.parseColor("#00FFCC"));

            try (InputStream is = getContentResolver().openInputStream(uri);
                 BufferedInputStream bis = new BufferedInputStream(is)) {

                if (bis == null) throw new Exception("Dosya okunamadi!");

                byte[] buffer = new byte[1024 * 512]; // 512KB parçalar (Hafızayı yormaz)
                int bytesRead;
                long totalBytesRead = 0;
                String leftover = "";
                int overlapSize = 100;

                while ((bytesRead = bis.read(buffer)) != -1) {
                    totalBytesRead += bytesRead;
                    
                    // Parçayı stringe çevir ve önceki parçanın sonuyla birleştir (kaçan kelime olmasın diye)
                    String chunk = leftover + new String(buffer, 0, bytesRead, "ISO-8859-1");
                    String lowerChunk = chunk.toLowerCase();
                    
                    for (String kw : KEYWORDS) {
                        int index = 0;
                        while ((index = lowerChunk.indexOf(kw, index)) != -1) {
                            int start = Math.max(0, index - 20);
                            int end = Math.min(chunk.length(), index + 70);
                            String foundStr = chunk.substring(start, end).replaceAll("[^\\x20-\\x7E]", ".");
                            
                            if (!uniqueFound.contains(foundStr) && foundStr.length() > 10) {
                                uniqueFound.add(foundStr);
                                totalFound++;
                                
                                if (totalFound <= MAX_RESULTS) {
                                    String displayStr = "[" + kw.toUpperCase() + "] ..." + foundStr + "...\n";
                                    appendColoredText(finalResults, displayStr, Color.parseColor("#FF0055"));
                                }
                            }
                            index += kw.length();
                        }
                    }

                    if (chunk.length() > overlapSize) leftover = chunk.substring(chunk.length() - overlapSize);
                    else leftover = chunk;

                    if (fileSize > 0) {
                        int progress = (int) ((totalBytesRead * 100) / fileSize);
                        final int fProgress = progress;
                        final String fSizeStr = formatSize(totalBytesRead) + " / " + formatSize(fileSize);
                        mainHandler.post(() -> {
                            progressBar.setIndeterminate(false);
                            progressBar.setProgress(fProgress);
                            tvStatus.setText("> Taraniyor: %" + fProgress + " (" + fSizeStr + ")");
                        });
                    }

                    if (totalFound >= MAX_RESULTS) break;
                }

                if (totalFound >= MAX_RESULTS) {
                    appendColoredText(finalResults, "\n[!] Limit asildi, diger sonuclar gizlendi.\n", Color.parseColor("#FFCC00"));
                } else if (totalFound == 0) {
                    appendColoredText(finalResults, "Hicbir gizli anahtar bulunamadi.\n", Color.parseColor("#888888"));
                }

                mainHandler.post(() -> {
                    tvStatus.setText("> Tamamlandi! (" + totalFound + " sonuc)");
                    tvResults.setText(finalResults);
                    progressBar.setVisibility(View.INVISIBLE);
                    btnSelect.setEnabled(true);
                });

            } catch (Exception e) {
                final String err = e.getMessage();
                mainHandler.post(() -> {
                    tvStatus.setText("> Hata: " + err);
                    progressBar.setVisibility(View.INVISIBLE);
                    btnSelect.setEnabled(true);
                    Toast.makeText(MainActivity.this, "Analiz hatasi!", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void appendColoredText(SpannableStringBuilder sb, String text, int color) {
        int start = sb.length();
        sb.append(text);
        sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return (size / 1024) + " KB";
        return (size / (1024 * 1024)) + " MB";
    }
}
