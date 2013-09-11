/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.documentsui;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.DocumentsContract.Root;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.documentsui.DocumentsActivity.State;
import com.android.documentsui.SectionedListAdapter.SectionAdapter;
import com.android.documentsui.model.DocumentInfo;
import com.android.documentsui.model.RootInfo;
import com.android.internal.util.Objects;

import java.util.Comparator;
import java.util.List;

/**
 * Display list of known storage backend roots.
 */
public class RootsFragment extends Fragment {

    private ListView mList;
    private SectionedRootsAdapter mAdapter;

    private static final String EXTRA_INCLUDE_APPS = "includeApps";

    public static void show(FragmentManager fm, Intent includeApps) {
        final Bundle args = new Bundle();
        args.putParcelable(EXTRA_INCLUDE_APPS, includeApps);

        final RootsFragment fragment = new RootsFragment();
        fragment.setArguments(args);

        final FragmentTransaction ft = fm.beginTransaction();
        ft.replace(R.id.container_roots, fragment);
        ft.commitAllowingStateLoss();
    }

    public static RootsFragment get(FragmentManager fm) {
        return (RootsFragment) fm.findFragmentById(R.id.container_roots);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final Context context = inflater.getContext();

        final View view = inflater.inflate(R.layout.fragment_roots, container, false);
        mList = (ListView) view.findViewById(android.R.id.list);
        mList.setOnItemClickListener(mItemListener);
        mList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        updateRootsAdapter();
    }

    private void updateRootsAdapter() {
        final Context context = getActivity();

        final State state = ((DocumentsActivity) context).getDisplayState();
        state.showAdvanced = SettingsActivity.getDisplayAdvancedDevices(context);

        final RootsCache roots = DocumentsApplication.getRootsCache(context);
        final List<RootInfo> matchingRoots = roots.getMatchingRoots(state);
        final Intent includeApps = getArguments().getParcelable(EXTRA_INCLUDE_APPS);

        mAdapter = new SectionedRootsAdapter(context, matchingRoots, includeApps);
        mList.setAdapter(mAdapter);

        onCurrentRootChanged();
    }

    public void onCurrentRootChanged() {
        if (mAdapter == null) return;

        final RootInfo root = ((DocumentsActivity) getActivity()).getCurrentRoot();
        for (int i = 0; i < mAdapter.getCount(); i++) {
            final Object item = mAdapter.getItem(i);
            if (Objects.equal(item, root)) {
                mList.setItemChecked(i, true);
                return;
            }
        }
    }

    private OnItemClickListener mItemListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            final DocumentsActivity activity = DocumentsActivity.get(RootsFragment.this);
            final Object item = mAdapter.getItem(position);
            if (item instanceof RootInfo) {
                activity.onRootPicked((RootInfo) item, true);
            } else if (item instanceof ResolveInfo) {
                activity.onAppPicked((ResolveInfo) item);
            } else {
                throw new IllegalStateException("Unknown root: " + item);
            }
        }
    };

    private static class RootsAdapter extends ArrayAdapter<RootInfo> implements SectionAdapter {
        private int mHeaderId;

        public RootsAdapter(Context context, int headerId) {
            super(context, 0);
            mHeaderId = headerId;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final Context context = parent.getContext();
            if (convertView == null) {
                convertView = LayoutInflater.from(context)
                        .inflate(R.layout.item_root, parent, false);
            }

            final ImageView icon = (ImageView) convertView.findViewById(android.R.id.icon);
            final TextView title = (TextView) convertView.findViewById(android.R.id.title);
            final TextView summary = (TextView) convertView.findViewById(android.R.id.summary);

            final RootInfo root = getItem(position);
            icon.setImageDrawable(root.loadIcon(context));
            title.setText(root.title);

            // Device summary is always available space
            final String summaryText;
            if (root.rootType == Root.ROOT_TYPE_DEVICE && root.availableBytes >= 0) {
                summaryText = context.getString(R.string.root_available_bytes,
                        Formatter.formatFileSize(context, root.availableBytes));
            } else {
                summaryText = root.summary;
            }

            summary.setText(summaryText);
            summary.setVisibility(TextUtils.isEmpty(summaryText) ? View.GONE : View.VISIBLE);

            return convertView;
        }

        @Override
        public View getHeaderView(View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_root_header, parent, false);
            }

            final TextView title = (TextView) convertView.findViewById(android.R.id.title);
            title.setText(mHeaderId);

            return convertView;
        }
    }

    private static class AppsAdapter extends ArrayAdapter<ResolveInfo> implements SectionAdapter {
        public AppsAdapter(Context context) {
            super(context, 0);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final Context context = parent.getContext();
            final PackageManager pm = context.getPackageManager();
            if (convertView == null) {
                convertView = LayoutInflater.from(context)
                        .inflate(R.layout.item_root, parent, false);
            }

            final ImageView icon = (ImageView) convertView.findViewById(android.R.id.icon);
            final TextView title = (TextView) convertView.findViewById(android.R.id.title);
            final TextView summary = (TextView) convertView.findViewById(android.R.id.summary);

            final ResolveInfo info = getItem(position);
            icon.setImageDrawable(info.loadIcon(pm));
            title.setText(info.loadLabel(pm));

            // TODO: match existing summary behavior from disambig dialog
            summary.setVisibility(View.GONE);

            return convertView;
        }

        @Override
        public View getHeaderView(View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_root_header, parent, false);
            }

            final TextView title = (TextView) convertView.findViewById(android.R.id.title);
            title.setText(R.string.root_type_apps);

            return convertView;
        }
    }

    private static class SectionedRootsAdapter extends SectionedListAdapter {
        private final RootsAdapter mServices;
        private final RootsAdapter mShortcuts;
        private final RootsAdapter mDevices;
        private final AppsAdapter mApps;

        public SectionedRootsAdapter(Context context, List<RootInfo> roots, Intent includeApps) {
            mServices = new RootsAdapter(context, R.string.root_type_service);
            mShortcuts = new RootsAdapter(context, R.string.root_type_shortcut);
            mDevices = new RootsAdapter(context, R.string.root_type_device);
            mApps = new AppsAdapter(context);

            for (RootInfo root : roots) {
                switch (root.rootType) {
                    case Root.ROOT_TYPE_SERVICE:
                        mServices.add(root);
                        break;
                    case Root.ROOT_TYPE_SHORTCUT:
                        mShortcuts.add(root);
                        break;
                    case Root.ROOT_TYPE_DEVICE:
                        mDevices.add(root);
                        break;
                }
            }

            if (includeApps != null) {
                final PackageManager pm = context.getPackageManager();
                final List<ResolveInfo> infos = pm.queryIntentActivities(
                        includeApps, PackageManager.MATCH_DEFAULT_ONLY);

                // Omit ourselves from the list
                for (ResolveInfo info : infos) {
                    if (!context.getPackageName().equals(info.activityInfo.packageName)) {
                        mApps.add(info);
                    }
                }
            }

            final RootComparator comp = new RootComparator();
            mServices.sort(comp);
            mShortcuts.sort(comp);
            mDevices.sort(comp);

            if (mServices.getCount() > 0) {
                addSection(mServices);
            }
            if (mShortcuts.getCount() > 0) {
                addSection(mShortcuts);
            }
            if (mDevices.getCount() > 0) {
                addSection(mDevices);
            }
            if (mApps.getCount() > 0) {
                addSection(mApps);
            }
        }
    }

    public static class RootComparator implements Comparator<RootInfo> {
        @Override
        public int compare(RootInfo lhs, RootInfo rhs) {
            if (lhs.authority == null) {
                return -1;
            } else if (rhs.authority == null) {
                return 1;
            }

            final int score = DocumentInfo.compareToIgnoreCaseNullable(lhs.title, rhs.title);
            if (score != 0) {
                return score;
            } else {
                return DocumentInfo.compareToIgnoreCaseNullable(lhs.summary, rhs.summary);
            }
        }
    }
}
