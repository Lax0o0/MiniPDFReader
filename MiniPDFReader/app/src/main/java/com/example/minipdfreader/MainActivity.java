package com.example.minipdfreader;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle;
import com.github.barteksc.pdfviewer.util.FitPolicy;

public class MainActivity extends AppCompatActivity {

    private PDFView pdfView;
    private TextView pageInfo;
    private TextView searchInfo;
    private EditText searchInput;
    private EditText pageInput;

    private int totalPages = 0;
    private int currentPage = 0;
    private Uri loadedUri = null;

    // File picker launcher
    private final ActivityResultLauncher<String> filePicker =
        registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) loadPdf(uri);
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Views
        pdfView    = findViewById(R.id.pdfView);
        pageInfo   = findViewById(R.id.pageInfo);
        searchInfo = findViewById(R.id.searchInfo);
        searchInput = findViewById(R.id.searchInput);
        pageInput   = findViewById(R.id.pageInput);

        Button prevBtn   = findViewById(R.id.prevBtn);
        Button nextBtn   = findViewById(R.id.nextBtn);
        Button searchBtn = findViewById(R.id.searchBtn);
        Button goToBtn   = findViewById(R.id.goToBtn);

        // --- Navigation buttons ---
        prevBtn.setOnClickListener(v -> navigatePage(currentPage - 1));
        nextBtn.setOnClickListener(v -> navigatePage(currentPage + 1));

        // --- Search ---
        searchBtn.setOnClickListener(v -> performSearch());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { performSearch(); return true; }
            return false;
        });

        // --- Go to page ---
        goToBtn.setOnClickListener(v -> goToPage());
        pageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) { goToPage(); return true; }
            return false;
        });

        // Check if opened via intent (e.g. from file manager)
        Intent intent = getIntent();
        if (intent != null && intent.getData() != null) {
            loadPdf(intent.getData());
        } else {
            // Ask permission then open file picker
            checkPermissionAndPick();
        }
    }

    // ─── Permission handling ──────────────────────────────────────────────────

    private void checkPermissionAndPick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ — READ_MEDIA_IMAGES covers PDF URIs via SAF; no runtime perm needed
            filePicker.launch("application/pdf");
        } else {
            String perm = Manifest.permission.READ_EXTERNAL_STORAGE;
            if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
                filePicker.launch("application/pdf");
            } else {
                ActivityCompat.requestPermissions(this, new String[]{perm}, 100);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 100 &&
            results.length > 0 &&
            results[0] == PackageManager.PERMISSION_GRANTED) {
            filePicker.launch("application/pdf");
        } else {
            Toast.makeText(this, "Permission denied — cannot open PDF", Toast.LENGTH_LONG).show();
        }
    }

    // ─── Load PDF ─────────────────────────────────────────────────────────────

    private void loadPdf(Uri uri) {
        loadedUri = uri;
        searchInfo.setVisibility(android.view.View.GONE);

        pdfView.fromUri(uri)
            .defaultPage(0)
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .fitEachPage(true)
            .pageFitPolicy(FitPolicy.WIDTH)
            .scrollHandle(new DefaultScrollHandle(this))
            .onLoad(nbPages -> {
                totalPages = nbPages;
                updatePageInfo(0);
            })
            .onPageChange((page, pageCount) -> {
                currentPage = page;
                updatePageInfo(page);
            })
            .load();
    }

    // ─── Navigation ───────────────────────────────────────────────────────────

    private void navigatePage(int page) {
        if (totalPages == 0) return;
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;
        pdfView.jumpTo(page, true);
    }

    private void goToPage() {
        String raw = pageInput.getText().toString().trim();
        if (raw.isEmpty()) { toast("Enter a page number"); return; }
        try {
            int page = Integer.parseInt(raw) - 1; // user sees 1-based
            if (page < 0 || page >= totalPages) {
                toast("Page must be 1 – " + totalPages);
                return;
            }
            pdfView.jumpTo(page, true);
            pageInput.setText("");
        } catch (NumberFormatException e) {
            toast("Invalid number");
        }
    }

    // ─── Search ───────────────────────────────────────────────────────────────
    // PDFView library does not have built-in text search; we show a helpful
    // message. For full search, MuPDF or PdfRenderer + iText would be needed.

    private void performSearch() {
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            searchInfo.setVisibility(android.view.View.GONE);
            return;
        }
        // Show info — real text search needs MuPDF native layer
        searchInfo.setText("🔍 Searching for: \"" + query + "\"  — Use Ctrl+F in desktop viewer for precise results");
        searchInfo.setVisibility(android.view.View.VISIBLE);
        toast("Tip: Full text search needs MuPDF. Use 'Go to page' to navigate.");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void updatePageInfo(int page) {
        pageInfo.setText((page + 1) + " / " + totalPages);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
