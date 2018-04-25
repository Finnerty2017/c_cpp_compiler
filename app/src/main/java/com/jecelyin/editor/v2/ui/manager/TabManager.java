/*
 * Copyright (C) 2016 Jecelyin Peng <jecelyin@gmail.com>
 *
 * This file is part of 920 Text Editor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jecelyin.editor.v2.ui.manager;

import android.database.DataSetObserver;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.view.View;

import com.duy.ccppcompiler.R;
import com.jecelyin.editor.v2.Pref;
import com.jecelyin.editor.v2.adapter.EditorPagerAdapter;
import com.jecelyin.editor.v2.adapter.TabAdapter;
import com.jecelyin.editor.v2.common.TabCloseListener;
import com.jecelyin.editor.v2.ui.activities.MainActivity;
import com.jecelyin.editor.v2.ui.editor.EditorDelegate;
import com.jecelyin.editor.v2.utils.DBHelper;
import com.jecelyin.editor.v2.utils.ExtGrep;
import com.jecelyin.editor.v2.view.EditorView;
import com.yqritc.recyclerviewflexibledivider.HorizontalDividerItemDecoration;

import java.io.File;
import java.util.ArrayList;

/**
 * @author Jecelyin Peng <jecelyin@gmail.com>
 */
public class TabManager implements ViewPager.OnPageChangeListener {
    private final MainActivity mainActivity;
    private final TabAdapter tabAdapter;
    private EditorPagerAdapter editorPagerAdapter;
    private boolean exitApp;

    public TabManager(MainActivity activity) {
        this.mainActivity = activity;

        this.tabAdapter = new TabAdapter();
        tabAdapter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTabMenuViewsClick(v);
            }
        });
        mainActivity.getTabRecyclerView().addItemDecoration(new HorizontalDividerItemDecoration.Builder(activity.getContext()).build());
        mainActivity.getTabRecyclerView().setAdapter(tabAdapter);

        initEditor();

        mainActivity.mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mainActivity.mDrawerLayout.openDrawer(GravityCompat.START);
            }
        });
        mainActivity.mTabPager.setOnPageChangeListener(this);
    }

    private void onTabMenuViewsClick(View v) {
        switch (v.getId()) {
            case R.id.close_image_view:
                closeTab((int) v.getTag());
                break;
            default:
                int position = (int) v.getTag();
                mainActivity.closeMenu();
                setCurrentTab(position);
                break;
        }
    }

    private void initEditor() {
        editorPagerAdapter = new EditorPagerAdapter(mainActivity);
        mainActivity.mTabPager.setAdapter(editorPagerAdapter); //优先，避免TabAdapter获取不到正确的CurrentItem

        if (Pref.getInstance(mainActivity).isOpenLastFiles()) {
            ArrayList<DBHelper.RecentFileItem> recentFiles = DBHelper.getInstance(mainActivity).getRecentFiles(true);

            File f;
            for (DBHelper.RecentFileItem item : recentFiles) {
                f = new File(item.path);
                if (!f.isFile())
                    continue;
                editorPagerAdapter.newEditor(false, f, item.offset, item.encoding);
                setCurrentTab(editorPagerAdapter.getCount() - 1); //fixme: auto load file, otherwise click other tab will crash by search result
            }
            editorPagerAdapter.notifyDataSetChanged();
            updateTabList();

            int lastTab = Pref.getInstance(mainActivity).getLastTab();
            setCurrentTab(lastTab);
        }

        editorPagerAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                updateTabList();

                if (!exitApp && editorPagerAdapter.getCount() == 0) {
                    newTab();
                }
            }
        });

        if (editorPagerAdapter.getCount() == 0)
            editorPagerAdapter.newEditor(mainActivity.getString(R.string.new_filename, editorPagerAdapter.countNoFileEditor() + 1), null);
    }

    public void newTab() {
        editorPagerAdapter.newEditor(mainActivity.getString(R.string.new_filename, editorPagerAdapter.getCount() + 1), null);
        setCurrentTab(editorPagerAdapter.getCount() - 1);
    }

    public boolean newTab(CharSequence content) {
        editorPagerAdapter.newEditor(mainActivity.getString(R.string.new_filename, editorPagerAdapter.getCount() + 1), content);
        setCurrentTab(editorPagerAdapter.getCount() - 1);
        return true;
    }

    public boolean newTab(ExtGrep grep) {
        editorPagerAdapter.newEditor(grep);
        setCurrentTab(editorPagerAdapter.getCount() - 1);
        return true;
    }

    public boolean newTab(File file, String encoding) {
        return newTab(file, 0, encoding);
    }

    public boolean newTab(File file, int offset, String encoding) {
        int count = editorPagerAdapter.getCount();
        for (int i = 0; i < count; i++) {
            EditorDelegate fragment = editorPagerAdapter.getItem(i);
            if (fragment.getPath() == null)
                continue;
            if (fragment.getPath().equals(file.getPath())) {
                setCurrentTab(i);
                return false;
            }
        }
        editorPagerAdapter.newEditor(file, offset, encoding);
        setCurrentTab(count);
        return true;
    }

    public int getTabCount() {
        if (tabAdapter == null)
            return 0;
        return tabAdapter.getItemCount();
    }

    public int getCurrentTab() {
        return mainActivity.mTabPager.getCurrentItem();
    }

    public void setCurrentTab(final int index) {
        mainActivity.mTabPager.setCurrentItem(index);
        tabAdapter.setCurrentTab(index);
        updateToolbar();
    }

    public void closeTab(int position) {
        editorPagerAdapter.removeEditor(position, new TabCloseListener() {
            @Override
            public void onClose(String path, String encoding, int offset) {
                DBHelper.getInstance(mainActivity).updateRecentFile(path, false);
                int currentTab = getCurrentTab();
                if (getTabCount() != 0) {
                    setCurrentTab(currentTab);
                }
            }
        });
    }

    public EditorPagerAdapter getEditorPagerAdapter() {
        return editorPagerAdapter;
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {
        tabAdapter.setCurrentTab(position);
    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    private void updateTabList() {
        tabAdapter.setTabInfoList(editorPagerAdapter.getTabInfoList());
        tabAdapter.notifyDataSetChanged();
    }

    public void updateEditorView(int index, EditorView editorView) {
        editorPagerAdapter.setEditorView(index, editorView);
    }

    public void onDocumentChanged(int index) {
        updateTabList();
        updateToolbar();
    }

    private void updateToolbar() {
        EditorDelegate delegate = editorPagerAdapter.getItem(getCurrentTab());
        if (delegate == null)
            return;
        mainActivity.mToolbar.setTitle(delegate.getToolbarText());
    }

    public boolean closeAllTabAndExitApp() {
        EditorDelegate.setDisableAutoSave(true);
        exitApp = true;
        if (mainActivity.mTabPager != null) {
            Pref.getInstance(mainActivity).setLastTab(getCurrentTab());
        }
        return editorPagerAdapter.removeAll(new TabCloseListener() {
            @Override
            public void onClose(String path, String encoding, int offset) {
                DBHelper.getInstance(mainActivity).updateRecentFile(path, encoding, offset);
                int count = getTabCount();
                if (count == 0) {
                    mainActivity.finish();
                } else {
                    editorPagerAdapter.removeAll(this);
                }
            }
        });
    }
}
