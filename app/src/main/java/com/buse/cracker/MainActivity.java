package com.buse.cracker;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private TextView tvStatus, tvResults;
    private ProgressBar progressBar;
    private Button btnSelect;
    
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://[a-zA-Z0-9./?=_%:-]+)");
    private static final Pattern KEY_PATTERN = Pattern.compile("(?i)(auth|key|license|hwid|verify|invalid|t\\.me|api\\.|expire|login|void|token|panel)");
    
    private static final int MAX_RESULTS = 150;
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

        btnSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, PICK_FILE_REQUEST);
            }
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

        new Thread(new Runnable() {
            @Override
            public void run() {
                long fileSize = 0;
                String fileName = "Bilinmeyen Dosya";

                try {
                    ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
                    if (pfd != null) {
                        fileSize = pfd.getStatSize();
                        pfd.close();
                    }
                } catch (Exception ignored) {}

                try {
                    Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex >= 0) fileName = cursor.getString(nameIndex);
                        cursor.close();
                    }
                } catch (Exception ignored) {}

                final String fName = fileName;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        tvStatus.setText("> Hedef: " + fName);
                    }
                });

                SpannableStringBuilder finalResults = new SpannableStringBuilder();
                Set<String> uniqueFound = new HashSet<>();
                int totalFound = 0;

                appendColoredText(finalResults, "================================\n", Color.parseColor("#00FFCC"), true);
                appendColoredText(finalResults, "      BUSE CRACKER ANALIZ        \n", Color.parseColor("#00FFCC"), true);
                appendColoredText(finalResults, "================================\n\n", Color.parseColor("#00FFCC"), true);

                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    if (is == null) throw new Exception("Dosya okunamadi!");
                    
                    BufferedInputStream bis = new BufferedInputStream(is);
                    byte[] buffer = new byte[1024 * 128];
                    int bytesRead;
                    long totalBytesRead = 0;
                    String leftover = "";

                    while ((bytesRead = bis.read(buffer)) != -1) {
                        totalBytesRead += bytesRead;
                        String chunk = leftover + new String(buffer, 0, bytesRead, "ISO-8859-1");
                        
                        Matcher urlMatcher = URL_PATTERN.matcher(chunk);
                        while (urlMatcher.find()) {
                            String url = urlMatcher.group(1);
                            if (!uniqueFound.contains(url) && totalFound < MAX_RESULTS) {
                                uniqueFound.add(url);
                                totalFound++;
                                appendColoredText(finalResults, "[LINK] " + url + "\n", Color.parseColor("#00FFCC"), false);
                            }
                        }

                        Matcher keyMatcher = KEY_PATTERN.matcher(chunk);
                        while (keyMatcher.find()) {
                            int start = Math.max(0, keyMatcher.start() - 15);
                            int end = Math.min(chunk.length(), keyMatcher.end() + 25);
                            String context = chunk.substring(start, end).replaceAll("[^\\x20-\\x7E]", ".");
                            if (!uniqueFound.contains(context) && totalFound < MAX_RESULTS) {
                                uniqueFound.add(context);
                                totalFound++;
                                appendColoredText(finalResults, "[KEY ] ..." + context + "...\n", Color.parseColor("#FF0055"), false);
                            }
                        }

                        if (chunk.length() > 100) leftover = chunk.substring(chunk.length() - 100);
                        else leftover = chunk;

                        if (fileSize > 0) {
                            int progress = (int) ((totalBytesRead * 100) / fileSize);
                            final int fProgress = progress;
                            final long fTotalBytesRead = totalBytesRead;
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    progressBar.setIndeterminate(false);
                                    progressBar.setProgress(fProgress);
                                    tvStatus.setText("> Taraniyor: %" + fProgress + " (" + formatSize(fTotalBytesRead) + ")");
                                }
                            });
                        }

                        if (totalFound >= MAX_RESULTS) break;
                    }
                    bis.close();
                    is.close();

                    if (totalFound >= MAX_RESULTS) {
                        appendColoredText(finalResults, "\n[!] Limit asildi, diger sonuclar gizlendi.\n", Color.parseColor("#FFCC00"), true);
                    } else if (totalFound == 0) {
                        appendColoredText(finalResults, "Hicbir gizli anahtar bulunamadi.\n", Color.parseColor("#888888"), true);
                    }

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            tvStatus.setText("> Islem Tamamlandi! (" + totalFound + " bulgu)");
                            tvResults.setText(finalResults);
                            progressBar.setVisibility(View.INVISIBLE);
                            btnSelect.setEnabled(true);
                        }
                    });

                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            tvStatus.setText("> Hata: " + e.getMessage());
                            progressBar.setVisibility(View.INVISIBLE);
                            btnSelect.setEnabled(true);
                            Toast.makeText(MainActivity.this, "Analiz hatasi!", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void appendColoredText(SpannableStringBuilder sb, String text, int color, boolean bold) {
        int start = sb.length();
        sb.append(text);
        sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (bold) {
            sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return (size / 1024) + " KB";
        return (size / (1024 * 1024)) + " MB";
    }
}
