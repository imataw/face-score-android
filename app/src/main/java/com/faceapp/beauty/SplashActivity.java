package com.faceapp.beauty;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 启动画面: 深色底 + 居中图标 + 应用名, 短暂展示后进入主 WebView 页 */
public class SplashActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#10163A"));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        int d = dp(110);
        logo.setLayoutParams(new LinearLayout.LayoutParams(d, d));

        TextView title = new TextView(this);
        title.setText("颜值评分");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        lpTitle.topMargin = dp(20);
        title.setLayoutParams(lpTitle);

        TextView sub = new TextView(this);
        sub.setText("AI 面部美学分析");
        sub.setTextColor(Color.parseColor("#9FB3E8"));
        sub.setTextSize(13);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        lpSub.topMargin = dp(6);
        sub.setLayoutParams(lpSub);

        root.addView(logo);
        root.addView(title);
        root.addView(sub);
        setContentView(root);

        // 主页面 post 里进入
        root.postDelayed(() -> {
            try {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                finish();
            } catch (Exception ignore) {}
        }, 1300);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}