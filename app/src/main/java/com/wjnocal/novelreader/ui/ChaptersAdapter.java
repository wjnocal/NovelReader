package com.wjnocal.novelreader.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wjnocal.novelreader.R;
import com.wjnocal.novelreader.data.ChapterEntity;

import java.util.ArrayList;
import java.util.List;

public class ChaptersAdapter extends RecyclerView.Adapter<ChaptersAdapter.ChapterViewHolder> {
    public interface Listener {
        void onChapterClick(ChapterEntity chapter);
    }

    private final Listener listener;
    private final List<ChapterEntity> chapters = new ArrayList<>();
    private int selectedIndex;
    private int normalTextColor = 0xFF222222;

    public ChaptersAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<ChapterEntity> newChapters, int selectedIndex) {
        chapters.clear();
        chapters.addAll(newChapters);
        this.selectedIndex = selectedIndex;
        notifyDataSetChanged();
    }

    public void setNormalTextColor(int normalTextColor) {
        this.normalTextColor = normalTextColor;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChapterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chapter, parent, false);
        return new ChapterViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChapterViewHolder holder, int position) {
        ChapterEntity chapter = chapters.get(position);
        holder.title.setText((position + 1) + ". " + chapter.title);
        boolean selected = chapter.chapterIndex == selectedIndex;
        holder.title.setSelected(selected);
        holder.title.setTextColor(selected ? 0xFFB95A52 : normalTextColor);
        holder.title.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        holder.itemView.setOnClickListener(v -> listener.onChapterClick(chapter));
    }

    @Override
    public int getItemCount() {
        return chapters.size();
    }

    static class ChapterViewHolder extends RecyclerView.ViewHolder {
        final TextView title;

        ChapterViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.chapterRowTitle);
        }
    }
}
