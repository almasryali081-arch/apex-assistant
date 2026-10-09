package com.example.apex;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String TAG = "Apex";
    private static final int REQ_MIC = 1;
    private static final String PREFS = "apex";
    private static final String KEY_API = "api_key";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_GEMINI = "gemini_key";
    private static final String KEY_GEMINI_MODEL = "gemini_model";
    private static final String DEFAULT_GEMINI_MODEL = "gemini-3.5-flash-lite";
    private static final String SYSTEM =
            "You are Apex, a concise and friendly voice assistant. "
            + "Reply briefly in the same language the user speaks (Arabic or English). "
            + "Your replies are read aloud, so avoid markdown, lists, and symbols.";
    private static final String READY = "اضغط الميكروفون وتكلم";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<String[]> history = new ArrayList<>(); // {role, content}

    private SharedPreferences prefs;
    private OrbView orb;
    private TextView stateView;
    private TextView logView;
    private ScrollView scroll;
    private EditText input;

    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(new Locale("ar"));
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.US);
                }
                ttsReady = true;
            }
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {
                main.post(() -> setMode(OrbView.SPEAKING, "Apex يتكلم"));
            }
            @Override public void onDone(String id) {
                main.post(() -> setMode(OrbView.IDLE, READY));
            }
            @Override public void onError(String id) {
                main.post(() -> setMode(OrbView.IDLE, READY));
            }
        });

        if (activeKey().isEmpty()) {
            appendLog("مرحباً، اضغط ⚙ وأضف مفتاح Claude API لتبدأ.");
        }
    }

    // ---------- UI ----------

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF040A1A);
        int pad = dp(20);
        root.setPadding(pad, dp(40), pad, pad);

        TextView title = new TextView(this);
        title.setText("APEX");
        title.setTextColor(0xFF22D3EE);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.4f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, match(LinearLayout.LayoutParams.WRAP_CONTENT));

        stateView = new TextView(this);
        stateView.setText(READY);
        stateView.setTextColor(0xFF6FA9C9);
        stateView.setTextSize(13);
        stateView.setGravity(Gravity.CENTER);
        root.addView(stateView, match(LinearLayout.LayoutParams.WRAP_CONTENT));

        orb = new OrbView(this);
        LinearLayout.LayoutParams orbLp = new LinearLayout.LayoutParams(dp(280), dp(280));
        orbLp.gravity = Gravity.CENTER_HORIZONTAL;
        orbLp.topMargin = dp(12);
        orbLp.bottomMargin = dp(8);
        root.addView(orb, orbLp);

        scroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextColor(0xFFE6F7FF);
        logView.setTextSize(16);
        logView.setLineSpacing(0, 1.25f);
        scroll.addView(logView);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        input = new EditText(this);
        input.setHint("اكتب رسالة...");
        input.setHintTextColor(0xFF6FA9C9);
        input.setTextColor(0xFFE6F7FF);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        row.addView(input, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button sendBtn = smallButton("➤");
        sendBtn.setOnClickListener(v -> sendTyped());
        row.addView(sendBtn);

        Button micBtn = smallButton("🎤");
        micBtn.setOnClickListener(v -> startListening());
        row.addView(micBtn);

        Button settingsBtn = smallButton("⚙");
        settingsBtn.setOnClickListener(v -> showSettings());
        row.addView(settingsBtn);

        root.addView(row, match(LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(52), dp(52));
        lp.leftMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout.LayoutParams match(int height) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void setMode(int mode, String label) {
        orb.setMode(mode);
        stateView.setText(label);
    }

    private void appendLog(String line) {
        logView.append(line + "\n\n");
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    // ---------- Settings ----------

    private boolean useGemini() {
        return "gemini".equals(prefs.getString(KEY_PROVIDER, "claude"));
    }

    private String activeKey() {
        return prefs.getString(useGemini() ? KEY_GEMINI : KEY_API, "").trim();
    }

    private EditText keyField(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setText(value);
        return e;
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        box.setPadding(p, dp(8), p, 0);

        RadioGroup group = new RadioGroup(this);
        RadioButton rbClaude = new RadioButton(this);
        rbClaude.setText("Claude");
        rbClaude.setId(View.generateViewId());
        RadioButton rbGemini = new RadioButton(this);
        rbGemini.setText("Gemini (طبقة مجانية)");
        rbGemini.setId(View.generateViewId());
        group.addView(rbClaude);
        group.addView(rbGemini);
        group.check(useGemini() ? rbGemini.getId() : rbClaude.getId());
        box.addView(group);

        EditText claudeKey = keyField("مفتاح Claude: sk-ant-...", prefs.getString(KEY_API, ""));
        EditText geminiKey = keyField("مفتاح Gemini", prefs.getString(KEY_GEMINI, ""));
        EditText geminiModel = new EditText(this);
        geminiModel.setHint("اسم نموذج Gemini");
        geminiModel.setSingleLine(true);
        geminiModel.setText(prefs.getString(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL));
        box.addView(claudeKey);
        box.addView(geminiKey);
        box.addView(geminiModel);

        new AlertDialog.Builder(this)
                .setTitle("الإعدادات")
                .setMessage("تُحفظ المفاتيح على هذا الهاتف فقط.")
                .setView(box)
                .setPositiveButton("حفظ", (d, w) -> {
                    String model = geminiModel.getText().toString().trim();
                    prefs.edit()
                            .putString(KEY_PROVIDER,
                                    group.getCheckedRadioButtonId() == rbGemini.getId() ? "gemini" : "claude")
                            .putString(KEY_API, claudeKey.getText().toString().trim())
                            .putString(KEY_GEMINI, geminiKey.getText().toString().trim())
                            .putString(KEY_GEMINI_MODEL, model.isEmpty() ? DEFAULT_GEMINI_MODEL : model)
                            .apply();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    // ---------- Microphone ----------

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setMode(OrbView.IDLE, "التعرف على الصوت غير متوفر على هذا الجهاز");
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(listener);
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar");
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        if (tts != null) tts.stop();
        recognizer.startListening(i);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_MIC && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else if (requestCode == REQ_MIC) {
            setMode(OrbView.IDLE, "أحتاج إذن الميكروفون");
        }
    }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) {
            setMode(OrbView.LISTENING, "أسمعك...");
        }
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {
            orb.setLevel(Math.max(0f, Math.min(1f, rmsdB / 12f)));
        }
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}
        @Override public void onError(int error) {
            setMode(OrbView.IDLE, "لم أسمعك، حاول مرة أخرى");
        }
        @Override public void onResults(Bundle results) {
            ArrayList<String> res = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (res != null && !res.isEmpty()) {
                send(res.get(0));
            } else {
                setMode(OrbView.IDLE, READY);
            }
        }
        @Override public void onPartialResults(Bundle partialResults) {}
        @Override public void onEvent(int eventType, Bundle params) {}
    };

    private void sendTyped() {
        String text = input.getText().toString().trim();
        input.setText("");
        send(text);
    }

    // ---------- Conversation ----------

    private void send(String text) {
        if (text == null || text.trim().isEmpty()) {
            setMode(OrbView.IDLE, READY);
            return;
        }
        appendLog("أنت: " + text);

        String apiKey = activeKey();
        if (apiKey.isEmpty()) {
            String msg = "أضف مفتاح API من زر ⚙ أولاً.";
            appendLog("Apex: " + msg);
            speak(msg);
            return;
        }

        setMode(OrbView.THINKING, "Apex يفكر...");
        final boolean gemini = useGemini();
        final String model = prefs.getString(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL);
        List<String[]> snapshot = new ArrayList<>(history);
        snapshot.add(new String[]{"user", text});

        new Thread(() -> {
            try {
                String reply = gemini
                        ? GeminiClient.ask(apiKey, model, SYSTEM, snapshot)
                        : ClaudeClient.ask(apiKey, SYSTEM, snapshot);
                main.post(() -> {
                    history.add(new String[]{"user", text});
                    history.add(new String[]{"assistant", reply});
                    while (history.size() > 20) {
                        history.remove(0);
                        history.remove(0);
                    }
                    appendLog("Apex: " + reply);
                    speak(reply);
                });
            } catch (Exception ex) {
                Log.e(TAG, "Claude request failed", ex);
                main.post(() -> {
                    appendLog("خطأ: " + ex.getMessage());
                    setMode(OrbView.IDLE, READY);
                });
            }
        }).start();
    }

    private void speak(String text) {
        if (!ttsReady) {
            setMode(OrbView.IDLE, READY);
            return;
        }
        int r = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "apex-" + System.nanoTime());
        if (r != TextToSpeech.SUCCESS) {
            setMode(OrbView.IDLE, READY);
        }
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
