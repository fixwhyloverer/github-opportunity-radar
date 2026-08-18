package com.fixwhyloverer.githubopportunityradar;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "radar_store";
    private static final String CACHE = "last_cache";
    private static final String FAVORITES = "favorites";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Opportunity> currentItems = new ArrayList<>();

    private SharedPreferences prefs;
    private LinearLayout list;
    private TextView status;
    private EditText queryInput;
    private ProgressBar progress;
    private String mode = "repo";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(buildUi());
        loadCachedResults();
        fetchRadar();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(248, 250, 252));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(18), dp(18), dp(12));
        root.addView(header);

        TextView title = text("GitHub 信息差雷达", 24, Color.rgb(15, 23, 42), true);
        header.addView(title);

        status = text("准备扫描公开信号", 13, Color.rgb(71, 85, 105), false);
        status.setPadding(0, dp(6), 0, dp(12));
        header.addView(status);

        LinearLayout switcher = new LinearLayout(this);
        switcher.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(switcher);

        Button repoButton = button("新项目", true);
        Button painButton = button("吐槽需求", false);
        switcher.addView(repoButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        switcher.addView(painButton, new LinearLayout.LayoutParams(0, dp(44), 1));

        queryInput = new EditText(this);
        queryInput.setSingleLine(true);
        queryInput.setHint("关键词，例如 ai agent / devtools / productivity");
        queryInput.setText("ai agent");
        queryInput.setTextSize(15);
        queryInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        queryInput.setPadding(dp(12), 0, dp(12), 0);
        queryInput.setBackgroundColor(Color.WHITE);
        header.addView(queryInput, new LinearLayout.LayoutParams(-1, dp(48)));

        Button scanButton = button("扫描", true);
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(-1, dp(46));
        scanParams.setMargins(0, dp(10), 0, 0);
        header.addView(scanButton, scanParams);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        header.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));

        ScrollView scrollView = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), 0, dp(14), dp(18));
        scrollView.addView(list);
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        repoButton.setOnClickListener(v -> {
            mode = "repo";
            repoButton.setSelected(true);
            painButton.setSelected(false);
            queryInput.setHint("关键词，例如 ai agent / devtools / productivity");
            fetchRadar();
        });
        painButton.setOnClickListener(v -> {
            mode = "issue";
            repoButton.setSelected(false);
            painButton.setSelected(true);
            queryInput.setHint("需求关键词，例如 slow / expensive / missing / alternative");
            fetchRadar();
        });
        scanButton.setOnClickListener(v -> fetchRadar());
        queryInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                fetchRadar();
                return true;
            }
            return false;
        });

        return root;
    }

    private void fetchRadar() {
        String query = queryInput == null ? "ai agent" : queryInput.getText().toString().trim();
        if (TextUtils.isEmpty(query)) {
            query = "ai agent";
        }
        String finalQuery = query;
        setLoading(true, "正在扫描 GitHub 公共信号...");
        executor.execute(() -> {
            try {
                List<Opportunity> items = "repo".equals(mode)
                        ? fetchRepositories(finalQuery)
                        : fetchIssues(finalQuery);
                cacheResults(items);
                mainHandler.post(() -> showResults(items, "已更新 " + items.size() + " 条机会信号"));
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    setLoading(false, "网络请求失败，已显示最近缓存");
                    Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
                    loadCachedResults();
                });
            }
        });
    }

    private List<Opportunity> fetchRepositories(String query) throws Exception {
        String createdAfter = dateDaysAgo(21);
        String encoded = urlEncode(query + " created:>" + createdAfter);
        String url = "https://api.github.com/search/repositories?q=" + encoded + "&sort=stars&order=desc&per_page=20";
        JSONObject root = new JSONObject(httpGet(url));
        JSONArray items = root.getJSONArray("items");
        List<Opportunity> result = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            Opportunity opportunity = new Opportunity();
            opportunity.type = "新项目";
            opportunity.title = item.optString("full_name");
            opportunity.summary = item.optString("description", "暂无描述");
            opportunity.url = item.optString("html_url");
            opportunity.stars = item.optInt("stargazers_count");
            opportunity.forks = item.optInt("forks_count");
            opportunity.comments = item.optInt("open_issues_count");
            opportunity.updatedAt = item.optString("updated_at");
            opportunity.language = item.optString("language", "Unknown");
            opportunity.score = scoreRepository(opportunity);
            result.add(opportunity);
        }
        return result;
    }

    private List<Opportunity> fetchIssues(String query) throws Exception {
        String issueQuery = query + " (\"feature request\" OR \"pain point\" OR expensive OR slow OR alternative) state:open";
        String url = "https://api.github.com/search/issues?q=" + urlEncode(issueQuery) + "&sort=comments&order=desc&per_page=20";
        JSONObject root = new JSONObject(httpGet(url));
        JSONArray items = root.getJSONArray("items");
        List<Opportunity> result = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            Opportunity opportunity = new Opportunity();
            opportunity.type = "需求吐槽";
            opportunity.title = item.optString("title");
            opportunity.summary = item.optString("body", "");
            if (opportunity.summary.length() > 160) {
                opportunity.summary = opportunity.summary.substring(0, 160) + "...";
            }
            opportunity.url = item.optString("html_url");
            opportunity.comments = item.optInt("comments");
            opportunity.updatedAt = item.optString("updated_at");
            opportunity.language = "Issue";
            opportunity.score = scoreIssue(opportunity);
            result.add(opportunity);
        }
        return result;
    }

    private String httpGet(String target) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(target).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "GitHubOpportunityRadarAndroid");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            body.append(line);
        }
        reader.close();
        connection.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("GitHub API 返回 " + code);
        }
        return body.toString();
    }

    private void showResults(List<Opportunity> items, String message) {
        setLoading(false, message);
        currentItems.clear();
        currentItems.addAll(items);
        list.removeAllViews();
        if (items.isEmpty()) {
            TextView empty = text("没有找到结果，换个关键词再试。", 15, Color.rgb(71, 85, 105), false);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty, new LinearLayout.LayoutParams(-1, dp(120)));
            return;
        }
        for (Opportunity item : items) {
            list.addView(card(item));
        }
    }

    private View card(Opportunity item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(12));
        card.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = text(item.type + "  " + item.score + "分", 13, scoreColor(item.score), true);
        top.addView(badge, new LinearLayout.LayoutParams(0, -2, 1));
        TextView meta = text(item.language, 12, Color.rgb(100, 116, 139), false);
        top.addView(meta);
        card.addView(top);

        TextView title = text(item.title, 17, Color.rgb(15, 23, 42), true);
        title.setPadding(0, dp(8), 0, dp(6));
        card.addView(title);

        TextView summary = text(clean(item.summary), 14, Color.rgb(51, 65, 85), false);
        card.addView(summary);

        TextView metrics = text(metrics(item), 12, Color.rgb(100, 116, 139), false);
        metrics.setPadding(0, dp(10), 0, dp(10));
        card.addView(metrics);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button open = button("打开来源", true);
        Button fav = button(isFavorite(item.url) ? "已收藏" : "收藏", false);
        actions.addView(open, new LinearLayout.LayoutParams(0, dp(42), 1));
        actions.addView(fav, new LinearLayout.LayoutParams(0, dp(42), 1));
        card.addView(actions);

        open.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(item.url))));
        fav.setOnClickListener(v -> {
            toggleFavorite(item);
            fav.setText(isFavorite(item.url) ? "已收藏" : "收藏");
        });
        return card;
    }

    private int scoreRepository(Opportunity item) {
        int score = 35;
        score += Math.min(25, item.stars * 2);
        score += Math.min(12, item.forks * 3);
        score += Math.min(10, item.comments);
        score += keywordScore(item.title + " " + item.summary);
        return Math.min(100, score);
    }

    private int scoreIssue(Opportunity item) {
        int score = 42;
        score += Math.min(25, item.comments * 2);
        score += keywordScore(item.title + " " + item.summary);
        return Math.min(100, score);
    }

    private int keywordScore(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.US);
        int score = 0;
        String[] highValue = {"paid", "pricing", "enterprise", "saas", "api", "agent", "workflow", "automation", "alternative", "expensive", "slow", "missing", "feature request"};
        for (String word : highValue) {
            if (lower.contains(word)) {
                score += 4;
            }
        }
        return Math.min(28, score);
    }

    private void cacheResults(List<Opportunity> items) throws JSONException {
        JSONArray array = new JSONArray();
        for (Opportunity item : items) {
            array.put(item.toJson());
        }
        prefs.edit().putString(CACHE, array.toString()).apply();
    }

    private void loadCachedResults() {
        String cache = prefs.getString(CACHE, "[]");
        try {
            JSONArray array = new JSONArray(cache);
            List<Opportunity> items = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                items.add(Opportunity.fromJson(array.getJSONObject(i)));
            }
            if (!items.isEmpty()) {
                showResults(items, "显示最近缓存，可重新扫描");
            }
        } catch (JSONException ignored) {
            prefs.edit().remove(CACHE).apply();
        }
    }

    private void toggleFavorite(Opportunity item) {
        Set<String> favorites = new HashSet<>(prefs.getStringSet(FAVORITES, new HashSet<>()));
        if (favorites.contains(item.url)) {
            favorites.remove(item.url);
            Toast.makeText(this, "已取消收藏", Toast.LENGTH_SHORT).show();
        } else {
            favorites.add(item.url);
            Toast.makeText(this, "已收藏", Toast.LENGTH_SHORT).show();
        }
        prefs.edit().putStringSet(FAVORITES, favorites).apply();
    }

    private boolean isFavorite(String url) {
        return prefs.getStringSet(FAVORITES, new HashSet<>()).contains(url);
    }

    private void setLoading(boolean loading, String message) {
        if (progress != null) {
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (status != null) {
            status.setText(message);
        }
    }

    private String metrics(Opportunity item) {
        if ("新项目".equals(item.type)) {
            return "Stars " + item.stars + "   Forks " + item.forks + "   Issues " + item.comments + "   更新 " + item.updatedAt;
        }
        return "Comments " + item.comments + "   更新 " + item.updatedAt;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.15f);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(primary ? Color.WHITE : Color.rgb(37, 99, 235));
        button.setBackgroundColor(primary ? Color.rgb(37, 99, 235) : Color.rgb(239, 246, 255));
        return button;
    }

    private int scoreColor(int score) {
        if (score >= 80) {
            return Color.rgb(22, 163, 74);
        }
        if (score >= 60) {
            return Color.rgb(217, 119, 6);
        }
        return Color.rgb(37, 99, 235);
    }

    private String clean(String input) {
        if (TextUtils.isEmpty(input)) {
            return "暂无摘要";
        }
        return input.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private String dateDaysAgo(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -days);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(calendar.getTimeInMillis()));
    }

    private String urlEncode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    static class Opportunity {
        String type;
        String title;
        String summary;
        String url;
        String updatedAt;
        String language;
        int stars;
        int forks;
        int comments;
        int score;

        JSONObject toJson() throws JSONException {
            JSONObject object = new JSONObject();
            object.put("type", type);
            object.put("title", title);
            object.put("summary", summary);
            object.put("url", url);
            object.put("updatedAt", updatedAt);
            object.put("language", language);
            object.put("stars", stars);
            object.put("forks", forks);
            object.put("comments", comments);
            object.put("score", score);
            return object;
        }

        static Opportunity fromJson(JSONObject object) {
            Opportunity item = new Opportunity();
            item.type = object.optString("type");
            item.title = object.optString("title");
            item.summary = object.optString("summary");
            item.url = object.optString("url");
            item.updatedAt = object.optString("updatedAt");
            item.language = object.optString("language");
            item.stars = object.optInt("stars");
            item.forks = object.optInt("forks");
            item.comments = object.optInt("comments");
            item.score = object.optInt("score");
            return item;
        }
    }
}
