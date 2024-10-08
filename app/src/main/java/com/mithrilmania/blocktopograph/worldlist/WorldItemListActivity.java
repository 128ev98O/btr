package com.mithrilmania.blocktopograph.worldlist;

import static android.os.Build.VERSION.SDK_INT;
import static android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.mithrilmania.blocktopograph.CreateWorldActivity;
import com.mithrilmania.blocktopograph.Log;
import com.mithrilmania.blocktopograph.R;
import com.mithrilmania.blocktopograph.World;
import com.mithrilmania.blocktopograph.backup.WorldBackups;
import com.mithrilmania.blocktopograph.utils.IoUtil;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class WorldItemListActivity extends AppCompatActivity {

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 4242;
    private static final String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    public static final int REQUEST_CODE_CREATE_WORLD = 2012;
    public static final int REQUEST_CODE_PICK_CUSTOM_WORLD_FOLDER = 6724;
    public static final String PREF_KEY_ACCEPT_DATA_USAGE = "accept_data_usage";
    /**
     * Whether or not the activity is in two-pane mode, d.e. running on a tablet.
     */
    private boolean mTwoPane;
    private WorldItemRecyclerViewAdapter worldItemAdapter;

    /**
     * Checks if the app has permission to write to device storage
     * <p>
     * If the app does not has permission then the user will be prompted to grant permissions
     * </p>
     */
    public static boolean verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        boolean hasPermission = true;
        for (String permission : PERMISSIONS_STORAGE) {
            int state = ActivityCompat.checkSelfPermission(activity, permission);
            if (state != PackageManager.PERMISSION_GRANTED) {
                hasPermission = false;
                break;
            }
        }

        if (SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
                intent.setData(uri);
                activity.startActivity(intent);
            }
        }

        if (!hasPermission) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
            return false;
        } else return true;
    }

    private void showFeedbackRequestDialogIfNeeded() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        if (prefs.getInt(PREF_KEY_ACCEPT_DATA_USAGE, 0) == 1) {
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.privacy_promo_title)
                .setMessage(R.string.privacy_promo_text)
                .setPositiveButton(R.string.privacy_promo_accept_btn, this::onAcceptedRequestDialog)
                .setNegativeButton(R.string.privacy_promo_reject_btn, this::onRejectedRequestDialog)
                .setCancelable(false)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        View view = dialog.findViewById(android.R.id.message);
        if (view instanceof TextView)
            ((TextView) view).setMovementMethod(LinkMovementMethod.getInstance());
        else Log.d(this, "cannot find android.R.id.message for privacy request dialog.");
    }

    private void onAcceptedRequestDialog(DialogInterface dialogInterface, int i) {
        getPreferences(MODE_PRIVATE).edit().putInt(PREF_KEY_ACCEPT_DATA_USAGE, 1).apply();
    }

    private void onRejectedRequestDialog(DialogInterface dialogInterface, int i) {
        finishAffinity();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_worldlist);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());
        setSupportActionBar(toolbar);
        showFeedbackRequestDialogIfNeeded();

        if (findViewById(R.id.worlditem_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;
        }

        FloatingActionButton fabChooseWorldFile = findViewById(R.id.fab_create);
        fabChooseWorldFile.setOnClickListener(view -> onClickCreateWorld());

        RecyclerView recyclerView = findViewById(R.id.worlditem_list);
        worldItemAdapter = new WorldItemRecyclerViewAdapter();
        recyclerView.setAdapter(this.worldItemAdapter);

        if (verifyStoragePermissions(this)) {
            //directly open the world list if we already have access
            worldItemAdapter.enable();
            checkNotWorldFound();
        }
    }

    public void showFileChooser() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addCategory(Intent.CATEGORY_DEFAULT);

        startActivityForResult(Intent.createChooser(intent, ""), REQUEST_CODE_PICK_CUSTOM_WORLD_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_CUSTOM_WORLD_FOLDER && resultCode == RESULT_OK) {
            if (data == null) return;
            openWorld(data.getData().getPath());
        }
    }

    public void checkNotWorldFound() {
        TextView text = findViewById(R.id.worlditem_no_world);
        if (worldItemAdapter.mWorlds.size() == 0) {
            text.setText(R.string.world_is_not_found);
        } else {
            text.setText("");
        }
    }

    private void onClickCreateWorld() {
        if (worldItemAdapter.isDisabled()) {
            Snackbar.make(getWindow().getDecorView(), R.string.no_read_write_access, Snackbar.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(this, CreateWorldActivity.class));
    }

    private boolean checkPermissions(@NonNull int[] grantResults) {
        for (int result : grantResults)
            if (result != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // If request is cancelled, the result arrays are empty.
        if (requestCode == REQUEST_EXTERNAL_STORAGE) {
            if (grantResults.length == PERMISSIONS_STORAGE.length && checkPermissions(grantResults)) {

                // permission was granted, yay!
                this.worldItemAdapter.enable();

            } else {

                // permission denied, boo!
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                TextView msg = new TextView(this);
                float dpi = this.getResources().getDisplayMetrics().density;
                msg.setPadding((int) (19 * dpi), (int) (5 * dpi), (int) (14 * dpi), (int) (5 * dpi));
                msg.setMaxLines(20);
                msg.setMovementMethod(LinkMovementMethod.getInstance());
                msg.setText(R.string.no_sdcard_access);
                builder.setView(msg)
                        .setTitle(R.string.action_help)
                        .setCancelable(true)
                        .setNeutralButton(android.R.string.ok, null)
                        .show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.world, menu);
        return true;
    }

    private void openWorld(String path) {
        if (path == null) return;
        String levelDat = "/level.dat";
        int levelIndex = path.lastIndexOf(levelDat);
        //if the path ends with /level.dat, remove it!
        if (levelIndex >= 0 && path.endsWith(levelDat))
            path = path.substring(0, levelIndex);

        File root = Environment.getExternalStorageDirectory();

        /* If on android 11 up path is "/tree/primary:..." remove prefix ":" and concat with
        defalt root path
        */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            path = path.split(":")[1];
            path = new File(root, path).toString();
        }

        File defaultPath = new File(root, "Android/data/com.mojang.minecraftpe/files/games/com.mojang/minecraftWorlds/");
        File worldFolder = new File(path);
        Log.d("FileChecker", String.valueOf(worldFolder.exists()));
        String errTitle = null, errMsg = String.format(getString(R.string.report_path_and_previous_search), path, defaultPath);
        if (!worldFolder.exists()) {
            errTitle = getString(R.string.no_file_folder_found_at_path);
        }
        if (!worldFolder.isDirectory()) {
            errTitle = getString(R.string.worldpath_is_not_directory);
        }
        if (!(new File(worldFolder, "level.dat").exists())) {
            errTitle = getString(R.string.no_level_dat_found);
        }
        if (errTitle != null) {
            new AlertDialog.Builder(this)
                    .setTitle(errTitle)
                    .setMessage(errMsg)
                    .setCancelable(true)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } else {

            try {
                World world = new World(worldFolder, this);

                if (mTwoPane) {
                    Bundle arguments = new Bundle();
                    arguments.putSerializable(World.ARG_WORLD_SERIALIZED, world);
                    WorldItemDetailFragment fragment = new WorldItemDetailFragment();
                    fragment.setArguments(arguments);
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.worlditem_detail_container, fragment)
                            .commit();
                } else {
                    Intent intent = new Intent(this, WorldItemDetailActivity.class);
                    intent.putExtra(World.ARG_WORLD_SERIALIZED, world);

                    startActivity(intent);
                }

            } catch (Exception e) {
                Snackbar.make(getWindow().getDecorView(), R.string.error_opening_world, Snackbar.LENGTH_SHORT)
                        .setAction("Action", null).show();
            }
        }
    }

    private void onOpenWorldClicked() {
        if (worldItemAdapter.isDisabled()) {
            Snackbar.make(getWindow().getDecorView(), R.string.no_read_write_access, Snackbar.LENGTH_SHORT).show();
            return;
        }
        final EditText pathText = new EditText(WorldItemListActivity.this);
        pathText.setHint(R.string.storage_path_here);

        AlertDialog.Builder alert = new AlertDialog.Builder(WorldItemListActivity.this);
        alert.setTitle(R.string.open_world_custom_path);
        alert.setView(pathText);
        alert.setNeutralButton("File Chooser", (dialog, id) -> showFileChooser());
        alert.setPositiveButton(R.string.open, (dialog, id) -> openWorld(pathText.getText().toString()));
        alert.setCancelable(true);
        alert.setNegativeButton(android.R.string.cancel, null);
        alert.show();
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //some text pop-up dialogs, some with simple HTML tags.
        switch (item.getItemId()) {
            case R.id.action_open: {
                onOpenWorldClicked();
                return true;
            }
            case R.id.action_about: {

                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
                TextView msg = new TextView(this);
                msg.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                float dpi = getResources().getDisplayMetrics().density;
                msg.setPadding((int) (19 * dpi), (int) (5 * dpi), (int) (14 * dpi), (int) (5 * dpi));
                msg.setMaxLines(20);
                msg.setMovementMethod(LinkMovementMethod.getInstance());
                msg.setText(R.string.app_about);
                builder.setView(msg)
                        .setTitle(R.string.action_about)
                        .setCancelable(true)
                        .setNeutralButton(android.R.string.ok, null)
                        .show();

                return true;
            }
            case R.id.action_help: {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                TextView msg = new TextView(this);
                float dpi = getResources().getDisplayMetrics().density;
                msg.setPadding((int) (19 * dpi), (int) (5 * dpi), (int) (14 * dpi), (int) (5 * dpi));
                msg.setMaxLines(20);
                msg.setMovementMethod(LinkMovementMethod.getInstance());
                msg.setText(R.string.app_help);
                builder.setView(msg)
                        .setTitle(R.string.action_help)
                        .setCancelable(true)
                        .setNeutralButton(android.R.string.ok, null)
                        .show();

                return true;
            }
//            case R.id.action_changelog: {
//                AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
//                TextView msg = new TextView(ctx);
//                float dpi = ctx.getResources().getDisplayMetrics().density;
//                msg.setPadding((int) (19 * dpi), (int) (5 * dpi), (int) (14 * dpi), (int) (5 * dpi));
//                msg.setMaxLines(20);
//                msg.setMovementMethod(LinkMovementMethod.getInstance());
//                String content = String.format(ctx.getResources().getString(R.string.app_changelog), BuildConfig.VERSION_NAME);
//                //noinspection deprecation
//                msg.setText(Html.fromHtml(content));
//                builder.setView(msg)
//                        .setTitle(R.string.action_changelog)
//                        .setCancelable(true)
//                        .setNeutralButton(android.R.string.ok, null)
//                        .show();
//
//                return true;
//            }
            default: {
                return false;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        worldItemAdapter.loadWorldList();
        checkNotWorldFound();
    }

    public class WorldItemRecyclerViewAdapter extends RecyclerView.Adapter<WorldItemRecyclerViewAdapter.ViewHolder> {

        private final List<World> mWorlds;

        private boolean disabled;

        WorldItemRecyclerViewAdapter() {
            mWorlds = new LinkedList<>();
            disabled = true;
        }

        public void enable() {
            disabled = false;
        }

        public boolean isDisabled() {
            return disabled;
        }

        @SuppressLint("NotifyDataSetChanged")
        public void loadWorldList() {
            if (disabled) return;
            mWorlds.clear();
            List<File> saveFolders = new ArrayList<>();

            File sd = Environment.getExternalStorageDirectory();

            saveFolders.add(new File(sd, "games/com.mojang/minecraftWorlds"));
            saveFolders.add(new File(sd, "Android/data/com.mojang.minecraftpe/files/games/com.mojang/minecraftWorlds"));

            for (int i = 0, saveFoldersSize = saveFolders.size(); i < saveFoldersSize; i++) {
                File dir = saveFolders.get(i);
                if (dir.exists() && dir.canRead() && dir.canWrite()) {
                    var files = dir.listFiles();
                    assert files != null;
                    for (var file : files) {
                        if (!file.isDirectory()) continue;
                        if (new File(file, "level.dat").exists() ||
                                new File(file, WorldBackups.BTG_BACKUPS).exists()) {
                            try {
                                mWorlds.add(new World(file, WorldItemListActivity.this));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
            checkNotWorldFound();

            Collections.sort(mWorlds, new Comparator<>() {
                @Override
                public int compare(World a, World b) {
                    try {
                        long tA = WorldListUtil.getLastPlayedTimestamp(a);
                        long tB = WorldListUtil.getLastPlayedTimestamp(b);
                        return Long.compare(tB, tA);
                    } catch (Exception e) {
                        Log.d(this, e);
                        return 0;
                    }
                }
            });

            this.notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.worlditem_list_content, parent, false);
            return new ViewHolder(view);
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
            holder.mWorld = mWorlds.get(position);
            holder.mWorldNameView.setText(holder.mWorld.getWorldDisplayName());
            holder.mWorldSize.setText(IoUtil.getFileSizeInText(FileUtils.sizeOf(holder.mWorld.worldFolder)));
            holder.mWorldGamemode.setText(WorldListUtil.getWorldGamemodeText(WorldItemListActivity.this, holder.mWorld));
            holder.mWorldLastPlayed.setText(WorldListUtil.getLastPlayedText(WorldItemListActivity.this, holder.mWorld));
            holder.mWorldLastOpenedVersion.setText(WorldListUtil.getLastOpenedVersion(holder.mWorld));
            holder.mWorldPath.setText(holder.mWorld.worldFolder.getName());


            holder.mView.setOnClickListener(v -> {
                if (mTwoPane) {
                    Bundle arguments = new Bundle();
                    arguments.putSerializable(World.ARG_WORLD_SERIALIZED, holder.mWorld);
                    WorldItemDetailFragment fragment = new WorldItemDetailFragment();
                    fragment.setArguments(arguments);
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.worlditem_detail_container, fragment)
                            .commit();
                } else {
                    Context context = v.getContext();
                    Intent intent = new Intent(context, WorldItemDetailActivity.class);
                    intent.putExtra(World.ARG_WORLD_SERIALIZED, holder.mWorld);

                    context.startActivity(intent);
                }
            });
        }

        @Override
        public int getItemCount() {
            return mWorlds.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final View mView;
            final TextView mWorldNameView;
            final TextView mWorldSize;
            final TextView mWorldGamemode;
            final TextView mWorldLastPlayed;
            final TextView mWorldLastOpenedVersion;
            final TextView mWorldPath;
            World mWorld;

            ViewHolder(View view) {
                super(view);
                mView = view;
                mWorldNameView = view.findViewById(R.id.world_name);
                mWorldSize = view.findViewById(R.id.world_size);
                mWorldGamemode = view.findViewById(R.id.world_gamemode);
                mWorldLastPlayed = view.findViewById(R.id.world_last_played);
                mWorldLastOpenedVersion = view.findViewById(R.id.world_last_opened_version);
                mWorldPath = view.findViewById(R.id.world_path);
            }
        }
    }
}

