package com.wjnocal.novelreader.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wjnocal.novelreader.R;
import com.wjnocal.novelreader.data.NoteEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.NoteViewHolder> {
    private static final String REPLY_PREFIX = "\u0001reply:";
    private static final String REPLY_SEPARATOR = "\u0001";

    public interface Listener {
        void onNoteClick(NoteEntity note);

        void onNoteLongClick(NoteEntity note);
    }

    private final Listener listener;
    private final List<NoteEntity> notes = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    public NotesAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<NoteEntity> newNotes) {
        notes.clear();
        notes.addAll(newNotes);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bookmark, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        NoteEntity note = notes.get(position);
        holder.title.setText(note.chapterTitle == null ? "阅读笔记" : note.chapterTitle);
        holder.title.setTextColor(0xFF222222);
        holder.meta.setText("第 " + (note.pageIndex + 1) + " 页 · " + dateFormat.format(new Date(note.createdAt)));
        holder.meta.setTextColor(0xFF666666);
        String chapter = note.chapterTitle == null ? "\u5f53\u524d\u7ae0\u8282" : note.chapterTitle;
        String body = displayNoteText(note.noteText);
        if (body == null || body.trim().isEmpty()) {
            body = note.selectedText;
            holder.summary.setText(body == null ? "" : body.trim());
        } else {
            holder.summary.setText(chapter + "\uff1a" + body.trim());
        }
        holder.summary.setTextColor(0xFF333333);
        holder.itemView.setBackgroundColor(0x00000000);
        holder.itemView.setOnClickListener(v -> listener.onNoteClick(note));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onNoteLongClick(note);
            return true;
        });
    }

    private String displayNoteText(String noteText) {
        if (noteText == null || !noteText.startsWith(REPLY_PREFIX)) {
            return noteText;
        }
        int end = noteText.indexOf(REPLY_SEPARATOR, REPLY_PREFIX.length());
        String content = end >= 0 && end + 1 < noteText.length() ? noteText.substring(end + 1) : "";
        return "\u56de\u590d \u9ed8\u8ba4\u7528\u6237\uff1a" + content;
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView meta;
        final TextView summary;

        NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.bookmarkTitle);
            meta = itemView.findViewById(R.id.bookmarkMeta);
            summary = itemView.findViewById(R.id.bookmarkSummary);
        }
    }
}
