package com.black.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int PICK_ICON = 41;
    private static final String PREFS = "black_settings";
    private static final String HIDDEN = "hidden_apps";
    private SharedPreferences prefs;
    private GridView grid;
    private final List<AppEntry> apps = new ArrayList<>();
    private AppEntry editing;

    static class AppEntry {
        String label, pkg;
        AppEntry(String l, String p) { label=l; pkg=p; }
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
        loadApps();
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); return t;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK);
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(22),dp(25),dp(16),dp(10));
        TextView title = text("BLACK", 27, Color.WHITE); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); title.setLetterSpacing(.28f);
        top.addView(title, new LinearLayout.LayoutParams(0,dp(48),1));
        Button manage = new Button(this); manage.setText("⋮"); manage.setTextColor(Color.WHITE); manage.setBackgroundColor(Color.TRANSPARENT); manage.setOnClickListener(v -> showMenu()); top.addView(manage);
        root.addView(top);
        TextView hint = text("YOUR APPS",10,0xff777777); hint.setLetterSpacing(.22f); hint.setPadding(dp(24),0,0,dp(12)); root.addView(hint);
        grid = new GridView(this); grid.setNumColumns(4); grid.setHorizontalSpacing(dp(4)); grid.setVerticalSpacing(dp(16)); grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH); grid.setSelector(android.R.color.transparent); grid.setPadding(dp(8),dp(8),dp(8),dp(20));
        root.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    private void loadApps() {
        apps.clear();
        Intent query = new Intent(Intent.ACTION_MAIN,null); query.addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> found = pm.queryIntentActivities(query,0);
        Set<String> hidden = prefs.getStringSet(HIDDEN, new HashSet<>());
        for (ResolveInfo r : found) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || hidden.contains(pkg)) continue;
            String label = r.loadLabel(pm).toString();
            apps.add(new AppEntry(label,pkg));
        }
        Collections.sort(apps, Comparator.comparing(a -> a.label.toLowerCase()));
        grid.setAdapter(new AppAdapter());
    }

    private String iconKey(String pkg) { return "icon_"+pkg; }
    private Drawable getIcon(AppEntry a) {
        String uri = prefs.getString(iconKey(a.pkg), null);
        if (uri != null) {
            try { return Drawable.createFromStream(getContentResolver().openInputStream(Uri.parse(uri)),null); }
            catch(Exception ignored) { }
        }
        try { return getPackageManager().getApplicationIcon(a.pkg); }
        catch(Exception e) { return getDrawable(android.R.drawable.sym_def_app_icon); }
    }

    private class AppAdapter extends BaseAdapter {
        @Override public int getCount(){return apps.size();}
        @Override public Object getItem(int p){return apps.get(p);}
        @Override public long getItemId(int p){return p;}
        @Override public View getView(int p, View convert, ViewGroup parent) {
            AppEntry a=apps.get(p);
            LinearLayout cell=new LinearLayout(MainActivity.this); cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER); cell.setPadding(dp(2),dp(4),dp(2),dp(4));
            ImageView icon=new ImageView(MainActivity.this); icon.setImageDrawable(getIcon(a)); icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            cell.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
            TextView name=text(a.label,10,Color.WHITE); name.setGravity(Gravity.CENTER); name.setMaxLines(1); name.setPadding(0,dp(6),0,0);
            cell.addView(name,new LinearLayout.LayoutParams(-1,-2));
            cell.setOnClickListener(v -> launch(a));
            cell.setOnLongClickListener(v -> { editApp(a); return true; });
            return cell;
        }
    }

    private void launch(AppEntry a) {
        Intent i=getPackageManager().getLaunchIntentForPackage(a.pkg);
        if(i!=null) { try { startActivity(i); } catch(Exception e){toast("Не удалось открыть приложение");} }
        else toast("Для приложения нет ярлыка запуска");
    }

    private void editApp(AppEntry a) {
        editing=a;
        String[] choices={"Выбрать свою иконку","Вернуть оригинальную иконку","Скрыть приложение"};
        new AlertDialog.Builder(this).setTitle(a.label).setItems(choices,(d,which)->{
            if(which==0) pickIcon();
            else if(which==1){prefs.edit().remove(iconKey(a.pkg)).apply();grid.setAdapter(new AppAdapter());}
            else hideApp(a);
        }).show();
    }

    private void pickIcon() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,PICK_ICON);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==PICK_ICON && resultCode==RESULT_OK && data!=null && editing!=null) {
            Uri uri=data.getData();
            try { getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(Exception ignored) {}
            prefs.edit().putString(iconKey(editing.pkg),uri.toString()).apply();
            grid.setAdapter(new AppAdapter()); toast("Иконка изменена");
        }
    }

    private void hideApp(AppEntry a) {
        Set<String> h=new HashSet<>(prefs.getStringSet(HIDDEN,new HashSet<>())); h.add(a.pkg); prefs.edit().putStringSet(HIDDEN,h).apply(); loadApps(); toast("Приложение скрыто в BLACK");
    }

    private void showMenu() {
        String[] choices={"Управление скрытыми приложениями","Обновить список приложений","О BLACK"};
        new AlertDialog.Builder(this).setTitle("BLACK").setItems(choices,(d,w)->{
            if(w==0) manageHidden();
            else if(w==1) loadApps();
            else new AlertDialog.Builder(this).setMessage("BLACK 1.0\nЛёгкий минималистичный лаунчер.\nИзменение иконок действует внутри BLACK.").setPositiveButton("OK",null).show();
        }).show();
    }

    private void manageHidden() {
        Set<String> hidden=new HashSet<>(prefs.getStringSet(HIDDEN,new HashSet<>()));
        if(hidden.isEmpty()){toast("Скрытых приложений нет");return;}
        List<String> pkgs=new ArrayList<>(hidden); List<String> labels=new ArrayList<>();
        for(String p:pkgs){try{labels.add(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(p,0)).toString());}catch(Exception e){labels.add(p);}}
        String[] arr=labels.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Нажми, чтобы вернуть приложение").setItems(arr,(d,w)->{
            hidden.remove(pkgs.get(w)); prefs.edit().putStringSet(HIDDEN,hidden).apply(); loadApps(); toast("Приложение восстановлено");
        }).setNegativeButton("Закрыть",null).show();
    }

    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
