/*
 *   Copyright 2020-2021 Rosemoe
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package io.github.rosemoe.editor.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import io.github.rosemoe.editor.interfaces.AutoCompleteProvider;
import io.github.rosemoe.editor.struct.CompletionItem;
import io.github.rosemoe.editor.text.CharPosition;
import io.github.rosemoe.editor.text.Cursor;
import io.github.rosemoe.editor.text.TextAnalyzeResult;

import com.tyron.code.model.TextEdit;
import com.tyron.code.completion.provider.CompletionProvider;
import com.tyron.code.completion.ParseTask;
import com.tyron.code.rewrite.AddImport;
import java.util.Map;
import java.io.File;

import android.util.Log;
import androidx.recyclerview.widget.RecyclerView;
import com.tyron.code.ui.editor.CompletionItemAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.tyron.code.completion.Parser;
import android.view.ViewGroup;
import com.tyron.code.util.AndroidUtilities;
/**
 * Auto complete window for editing code quicker
 *
 * @author Rose
 */
public class EditorAutoCompleteWindow extends EditorBasePopupWindow {
    private final static String TIP = "Refreshing...";
    private final CodeEditor mEditor;
    private final LinearLayoutManager mLayoutManager;
    private final RecyclerView mListView;
    private final TextView mTip;
    private final GradientDrawable mBg;
    protected boolean mCancelShowUp = false;
    private int mCurrent = 0;
    private long mRequestTime;
    private String mLastPrefix;
    private AutoCompleteProvider mProvider;
    private boolean mLoading;
    private int mMaxHeight;
    private final CompletionItemAdapter mAdapter;
    
    /**
     * Create a panel instance for the given editor
     *
     * @param editor Target editor
     */
    public EditorAutoCompleteWindow(CodeEditor editor) {
        super(editor);
        mEditor = editor;
      /*  mAdapter = new DefaultCompletionItemAdapter();
        RelativeLayout layout = new RelativeLayout(mEditor.getContext());
        mListView = new ListView(mEditor.getContext());
        layout.addView(mListView, new LinearLayout.LayoutParams(-1, -1));
        mTip = new TextView(mEditor.getContext());
        mTip.setText(TIP);
        mTip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mTip.setBackgroundColor(0xeeeeeeee);
        mTip.setTextColor(0xff000000);
        layout.addView(mTip);
        ((RelativeLayout.LayoutParams) mTip.getLayoutParams()).addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        setContentView(layout);
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(1);
        layout.setBackgroundDrawable(gd);
        mBg = gd;
        applyColorScheme();
        mListView.setDividerHeight(0);
        setLoading(true);
        mListView.setOnItemClickListener((parent, view, position, id) -> {
            try {
                select(position);
            } catch (Exception e) {
                Toast.makeText(mEditor.getContext(), Log.getStackTraceString(e), Toast.LENGTH_SHORT).show();
            }
        });*/
        
        RelativeLayout layout = new RelativeLayout(mEditor.getContext());
        setContentView(layout);

        mAdapter = new CompletionItemAdapter();
        mAdapter.setOnItemClickListener(new CompletionItemAdapter.OnClickListener() {
                @Override
                public void onClick(int position) {
                    try {
                        select(position);
                    } catch (Exception e) {
                        Toast.makeText(mEditor.getContext(), Log.getStackTraceString(e), Toast.LENGTH_SHORT).show();
                    }
                }
        });
        mLayoutManager = new LinearLayoutManager(mEditor.getContext());
        mListView = new RecyclerView(mEditor.getContext()) {
			@Override
			public void onMeasure(int widthSpec, int heightSpec) {
			    // we subtract the height of the shortcuts view so the window wont obscure it
                int height = mMaxHeight - AndroidUtilities.dp(38);
                if (height < 1) {
                    height = mMaxHeight;
                }
				heightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST);
				super.onMeasure(widthSpec, heightSpec);
			}
		};
        mListView.setLayoutManager(mLayoutManager);
        mListView.setAdapter(mAdapter);

        layout.addView(mListView, new ViewGroup.LayoutParams(-1, -2));
		
        mTip = new TextView(mEditor.getContext());
        mTip.setText(TIP);
        mTip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mTip.setBackgroundColor(0xeeeeeeee);
        mTip.setTextColor(0xff000000);
        layout.addView(mTip);
        ((RelativeLayout.LayoutParams) mTip.getLayoutParams()).addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(1);
        layout.setBackgroundDrawable(gd);
        mBg = gd;
        
        applyColorScheme();
        setLoading(true);
		setWindowLayoutMode(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);

        
    }
    
    /**
     * Not needed
     */
    protected void setAdapter(EditorCompletionAdapter adapter) {
        /*mAdapter = adapter;
        if (adapter == null) {
            mAdapter = new DefaultCompletionItemAdapter();
        }*/
    }

    @Override
    public void show() {
        if (mCancelShowUp) {
            return;
        }
        super.show();
    }

    public Context getContext() {
        return mEditor.getContext();
    }

    public int getCurrentPosition() {
        return mCurrent;
    }

    /**
     * Set a auto completion items provider
     *
     * @param provider New provider.can not be null
     */
    public void setProvider(AutoCompleteProvider provider) {
        mProvider = provider;
    }

    /**
     * Apply colors for self
     */
    public void applyColorScheme() {
        EditorColorScheme colors = mEditor.getColorScheme();
        mBg.setStroke(2, 0xff575757);
        mBg.setColor(0xff2b2b2b);
    }

    /**
     * Change layout to loading/idle
     *
     * @param state Whether loading
     */
    public void setLoading(boolean state) {
        mLoading = state;
        if (state) {
            mEditor.postDelayed(() -> {
                if (mLoading) {
                    mTip.setVisibility(View.VISIBLE);
                }
            }, 300);
        } else {
            mTip.setVisibility(View.GONE);
        }
        //mListView.setVisibility((!state) ? View.VISIBLE : View.GONE);
        update();
    }

    /**
     * Move selection down
     */
    public void moveDown() {
        if (mCurrent + 1 >= mListView.getAdapter().getItemCount()) {
            return;
        }
        mCurrent++;
        ensurePosition();
    }

    /**
     * Move selection up
     */
    public void moveUp() {
        if (mCurrent - 1 < 0) {
            return;
        }
        mCurrent--;
        ensurePosition();
    }

    /**
     * Make current selection visible
     */
    private void ensurePosition() {
        mListView.scrollToPosition(mCurrent);
        mAdapter.setSelection(mCurrent);
    }

    /**
     * Select current position
     */
    public void select() {
        select(mCurrent);
    }

    private String selectedItem;

    /**
     * Select the given position
     *
     * @param pos Index of auto complete item
     */
    @SuppressLint("NewApi")
    public void select(int pos) {
        CompletionItem item = mAdapter.getItem(pos);
        Cursor cursor = mEditor.getCursor();
        if (!cursor.isSelected()) {
            mCancelShowUp = true;

			int length = mLastPrefix.length();
			
			if (mLastPrefix.contains(".")) {
				length -= mLastPrefix.lastIndexOf(".") + 1;
			}
            mEditor.getText().delete(cursor.getLeftLine(), cursor.getLeftColumn() - length, cursor.getLeftLine(), cursor.getLeftColumn());

			selectedItem = item.commit;
            // cursor.onCommitText(item.commit); will be invoked automatically if item.commit isn't multiline
            cursor.onCommitMultilineText(item.commit);
			
            if (item.cursorOffset != item.commit.length()) {
                int delta = (item.commit.length() - item.cursorOffset);
                if (delta != 0) {
                    int newSel = Math.max(mEditor.getCursor().getLeft() - delta, 0);
                    CharPosition charPosition = mEditor.getCursor().getIndexer().getCharPosition(newSel);
                    mEditor.setSelection(charPosition.line, charPosition.column);
                }
            }
			
            if (item.item.action == com.tyron.code.model.CompletionItem.Kind.IMPORT) {
                Parser parser = Parser.parseFile(mEditor.getCurrentFile().toPath());
                ParseTask task = new ParseTask(parser.task, parser.root);
                Log.d("PackageName", task.root.getPackageName().toString());
                
                boolean samePackage = false;
                if (!item.item.data.contains(".") //it's either in the same class or it's already imported
                        || task.root.getPackageName().toString().equals(getAfterLastDot(item.item.data))) {
                    samePackage = true;
                }
                
                if (!samePackage && !CompletionProvider.hasImport(task.root, item.item.data)) {
                    AddImport imp = new AddImport(new File(""), item.item.data);
                    Map<File, TextEdit> edits = imp.getText(task);
                    TextEdit edit = edits.values().iterator().next();
                    
                    if (edit.start.equals(edit.end)) {
                        mEditor.getText().insert(edit.start.line, edit.start.column, edit.edit);
                    }
                }
            }
            mCancelShowUp = false;
        }
        mEditor.postHideCompletionWindow();
    }

    /**
     * Get prefix set
     *
     * @return The previous prefix
     */
    public String getPrefix() {
        return mLastPrefix;
    }

    /**
     * Set prefix for auto complete analysis
     *
     * @param prefix The user's input code's prefix
     */
    public void setPrefix(final String prefix) {
        if (mCancelShowUp) {
            return;
        }

        if (getAfterLastDot(prefix).equals(selectedItem) && !prefix.endsWith(".")) {
            selectedItem = "";
            return;
        }

        setLoading(true);
        mLastPrefix = prefix;
        mRequestTime = System.currentTimeMillis();
        new MatchThread(mRequestTime, prefix).start();
    }

    public void setMaxHeight(int height) {
        mMaxHeight = height;
    }
	
    /**
     * Display result of analysis
     *
     * @param results     Items of analysis
     * @param requestTime The time that this thread starts
     */
    private void displayResults(final List<CompletionItem> results, long requestTime) {
        if (mRequestTime != requestTime) {
            return;
        }

        if (mLastPrefix.equals(selectedItem)) {
            selectedItem = "";
            return;
        }

        mEditor.post(() -> {
            setLoading(false);
            if (results == null || results.isEmpty()) {
                hide();
                return;
            }
			
			mAdapter.attachAttributes(this, results);
            mListView.scrollToPosition(0);
            mCurrent = 0;
            mAdapter.setSelection(0);
			
            if (!isShowing()) {
				show();
			}
			update();
        });
    }

    /**
     * Analysis thread
     *
     * @author Rose
     */
    private class MatchThread extends Thread {

        private final long mTime;
        private final String mPrefix;
        private final boolean mInner;
        private final TextAnalyzeResult mColors;
        private final int mLine;
        private final AutoCompleteProvider mLocalProvider = mProvider;

        public MatchThread(long requestTime, String prefix) {
            mTime = requestTime;
            mPrefix = prefix;
            mColors = mEditor.getTextAnalyzeResult();
            mLine = mEditor.getCursor().getLeftLine();
            mInner = (!mEditor.isHighlightCurrentBlock()) || (mEditor.getBlockIndex() != -1);
        }

        @Override
        public void run() {
            try {
                displayResults(mLocalProvider.getAutoCompleteItems(mPrefix, mInner, mColors, mLine), mTime);
            } catch (Exception e) {
                e.printStackTrace();
                displayResults(new ArrayList<>(), mTime);
            }
        }


    }

    private String getAfterLastDot(String str) {
        if (str == null) {
            return "";
        }
        if (str.contains(".")) {
            str = str.substring(str.lastIndexOf(".") + 1);
        }
        return str;
    }
}

