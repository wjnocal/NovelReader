package com.wjnocal.novelreader.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wjnocal.novelreader.R;
import com.wjnocal.novelreader.data.BookmarkEntity;

import java.util.ArrayList;
import java.util.List;

public class BookmarksAdapter extends RecyclerView.Adapter<BookmarksAdapter.BookmarkViewHolder> {
    public interface Listener {
        void onBookmarkClick(BookmarkEntity bookmark);
    }

    private final Listener listener;
    private final List<BookmarkEntity> bookmarks = new ArrayList<>();

    public BookmarksAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<BookmarkEntity> newBookmarks) {
        bookmarks.clear();
        bookmarks.addAll(newBookmarks);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BookmarkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bookmark, parent, false);
        return new BookmarkViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookmarkViewHolder holder, int position) {
        BookmarkEntity bookmark = bookmarks.get(position);
        holder.title.setText(bookmark.chapterTitle);
        holder.title.setTextColor(0xFF222222);
        holder.meta.setText("第 " + (bookmark.pageIndex + 1) + " 页");
        holder.meta.setTextColor(0xFF666666);
        holder.summary.setText(bookmark.summary);
        holder.summary.setTextColor(0xFF333333);
        holder.itemView.setBackgroundColor(0x00000000);
        holder.itemView.setOnClickListener(v -> listener.onBookmarkClick(bookmark));
    }

    @Override
    public int getItemCount() {
        return bookmarks.size();
    }

    static class BookmarkViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView meta;
        final TextView summary;

        BookmarkViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.bookmarkTitle);
            meta = itemView.findViewById(R.id.bookmarkMeta);
            summary = itemView.findViewById(R.id.bookmarkSummary);
        }
    }
}
