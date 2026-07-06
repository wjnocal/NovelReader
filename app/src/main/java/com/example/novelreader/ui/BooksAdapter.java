package com.example.novelreader.ui;

import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.novelreader.R;
import com.example.novelreader.data.BookEntity;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BooksAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int DISPLAY_GRID = 0;
    public static final int DISPLAY_LIST = 1;
    private static final int TYPE_BOOK_GRID = 0;
    private static final int TYPE_BOOK_LIST = 1;
    private static final int TYPE_FOLDER = 2;
    private static final int TYPE_ADD_BOOK = 3;

    public interface Listener {
        void onBookClick(BookEntity book);

        void onBookLongClick(BookEntity book, View anchor);

        void onFolderClick(String folderName);

        void onFolderLongClick(String folderName, View anchor);

        void onAddToFolderClick(String folderName);
    }

    public static class FolderItem {
        public final String name;
        public final List<BookEntity> books;
        public final long pinnedAt;
        public final long updatedAt;

        public FolderItem(String name, List<BookEntity> books, long pinnedAt, long updatedAt) {
            this.name = name;
            this.books = books;
            this.pinnedAt = pinnedAt;
            this.updatedAt = updatedAt;
        }
    }

    public static class AddBookItem {
        public final String folderName;

        public AddBookItem(String folderName) {
            this.folderName = folderName;
        }
    }

    private final Listener listener;
    private final List<Object> items = new ArrayList<>();
    private final Set<Long> selectedBookIds = new HashSet<>();
    private boolean selectionMode;
    private int displayMode = DISPLAY_GRID;
    private long highlightedBookId = -1L;
    private boolean highlightOn;

    public BooksAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitBooks(List<BookEntity> books) {
        items.clear();
        items.addAll(books);
        selectedBookIds.retainAll(getBookIds(books));
        notifyDataSetChanged();
    }

    public void submitShelfItems(List<Object> newItems) {
        items.clear();
        items.addAll(newItems);
        selectedBookIds.retainAll(getVisibleBookIds(newItems));
        notifyDataSetChanged();
    }

    public void setDisplayMode(int displayMode) {
        this.displayMode = displayMode;
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean selectionMode) {
        this.selectionMode = selectionMode;
        if (!selectionMode) {
            selectedBookIds.clear();
        }
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void setBookSelected(BookEntity book, boolean selected) {
        if (selected) {
            selectedBookIds.add(book.id);
        } else {
            selectedBookIds.remove(book.id);
        }
        notifyDataSetChanged();
    }

    public void toggleBookSelected(BookEntity book) {
        if (selectedBookIds.contains(book.id)) {
            selectedBookIds.remove(book.id);
        } else {
            selectedBookIds.add(book.id);
        }
        notifyDataSetChanged();
    }

    public int getSelectedCount() {
        return selectedBookIds.size();
    }

    public List<BookEntity> getSelectedBooks() {
        List<BookEntity> selectedBooks = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof BookEntity) {
                BookEntity book = (BookEntity) item;
                if (selectedBookIds.contains(book.id)) {
                    selectedBooks.add(book);
                }
            }
        }
        return selectedBooks;
    }

    public int findPositionById(long bookId) {
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof BookEntity && ((BookEntity) item).id == bookId) {
                return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    public boolean isFolderPosition(int position) {
        return position >= 0 && position < items.size() && items.get(position) instanceof FolderItem;
    }

    public void setHighlight(long bookId, boolean highlightOn) {
        highlightedBookId = bookId;
        this.highlightOn = highlightOn;
        notifyDataSetChanged();
    }

    public void clearHighlight() {
        highlightedBookId = -1L;
        highlightOn = false;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        Object item = items.get(position);
        if (item instanceof FolderItem) {
            return TYPE_FOLDER;
        }
        if (item instanceof AddBookItem) {
            return TYPE_ADD_BOOK;
        }
        return displayMode == DISPLAY_LIST ? TYPE_BOOK_LIST : TYPE_BOOK_GRID;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_FOLDER) {
            View view = inflater.inflate(R.layout.item_folder, parent, false);
            return new FolderViewHolder(view);
        }
        if (viewType == TYPE_ADD_BOOK) {
            View view = inflater.inflate(R.layout.item_add_book, parent, false);
            return new AddBookViewHolder(view);
        }
        int layoutRes = viewType == TYPE_BOOK_LIST ? R.layout.item_book_list : R.layout.item_book;
        View view = inflater.inflate(layoutRes, parent, false);
        return new BookViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = items.get(position);
        if (item instanceof FolderItem) {
            bindFolder((FolderViewHolder) holder, (FolderItem) item);
        } else if (item instanceof AddBookItem) {
            bindAddBook((AddBookViewHolder) holder, (AddBookItem) item);
        } else {
            bindBook((BookViewHolder) holder, (BookEntity) item);
        }
    }

    private void bindFolder(FolderViewHolder holder, FolderItem folder) {
        holder.title.setText(folder.name);
        holder.count.setText(folder.books.size() + "本");
        holder.summary.setText(folderSummary(folder.books));
        holder.badge.setText(shortFolderName(folder.name));
        bindFolderCover(holder, folder.books);
        holder.pinnedIndicator.setVisibility(folder.pinnedAt > 0 ? View.VISIBLE : View.GONE);
        holder.itemView.setOnClickListener(v -> listener.onFolderClick(folder.name));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onFolderLongClick(folder.name, holder.itemView);
            return true;
        });
    }

    private void bindAddBook(AddBookViewHolder holder, AddBookItem item) {
        holder.itemView.setOnClickListener(v -> listener.onAddToFolderClick(item.folderName));
        holder.itemView.setOnLongClickListener(v -> true);
    }

    private void bindBook(BookViewHolder holder, BookEntity book) {
        holder.title.setText(book.title);
        int chapterNumber = book.totalChapters == 0 ? 0 : Math.min(book.currentChapterIndex + 1, book.totalChapters);
        int progressPercent = book.totalChapters == 0 ? 0 : Math.round(chapterNumber * 100f / book.totalChapters);
        String category = book.category == null || book.category.trim().isEmpty() ? "未分类" : book.category.trim();
        String status = book.finishedAt > 0 ? "已读" : "在读";
        boolean missing = isFileMissing(book);
        String statusBadge = missing ? "缺失" : status;
        holder.statusBadge.setText(statusBadge);
        holder.statusBadge.setBackgroundColor(missing ? 0xDD777777 : (book.finishedAt > 0 ? 0xDD5A8F62 : 0xDDB95A52));
        if (displayMode == DISPLAY_LIST) {
            if (holder.coverImage != null) {
                holder.coverImage.setVisibility(View.GONE);
            }
            holder.coverTitle.setText("");
            String source = book.author == null || book.author.trim().isEmpty() ? "未知来源" : book.author;
            holder.meta.setText(source + " · " + safeUpper(book.fileType) + " · " + category);
            holder.progress.setText("上次读到：第 " + chapterNumber + " 章 · 进度 " + chapterNumber + "/" + book.totalChapters + " · " + status + " " + progressPercent + "%");
        } else {
            bindBookCover(holder, book);
            holder.meta.setText("- " + category + " -");
            holder.progress.setText("上次读到：第 " + chapterNumber + " 章 · " + status + " " + progressPercent + "%");
        }
        boolean selected = selectedBookIds.contains(book.id);
        boolean highlighted = highlightedBookId == book.id && highlightOn;
        holder.pinnedIndicator.setVisibility(book.pinnedAt > 0 ? View.VISIBLE : View.GONE);
        holder.card.setCardBackgroundColor(highlighted ? 0xE0D6C8BF : (selected ? 0xCCFFFFFF : 0x80FFFFFF));
        holder.card.setStrokeColor(highlighted ? 0xFF111111 : (selected ? 0xFFB95A52 : 0x80FFFFFF));
        holder.card.setStrokeWidth(highlighted ? 5 : (selected ? 4 : 1));
        holder.itemView.setOnClickListener(v -> listener.onBookClick(book));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onBookLongClick(book, holder.itemView);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static Set<Long> getVisibleBookIds(List<Object> items) {
        Set<Long> ids = new HashSet<>();
        for (Object item : items) {
            if (item instanceof BookEntity) {
                ids.add(((BookEntity) item).id);
            }
        }
        return ids;
    }

    private static Set<Long> getBookIds(List<BookEntity> books) {
        Set<Long> ids = new HashSet<>();
        for (BookEntity book : books) {
            ids.add(book.id);
        }
        return ids;
    }

    private static String folderSummary(List<BookEntity> books) {
        if (books.isEmpty()) {
            return "暂无书籍";
        }
        StringBuilder builder = new StringBuilder();
        int count = Math.min(3, books.size());
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append("、");
            }
            builder.append(books.get(i).title);
        }
        if (books.size() > count) {
            builder.append("等");
        }
        return builder.toString();
    }

    private static String shortFolderName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "分组";
        }
        String clean = name.trim();
        return clean.length() <= 3 ? clean : clean.substring(0, 3);
    }

    private static void bindBookCover(BookViewHolder holder, BookEntity book) {
        File coverFile = book.coverPath == null || book.coverPath.trim().isEmpty() ? null : new File(book.coverPath);
        if (coverFile != null && coverFile.exists() && holder.coverImage != null) {
            holder.coverImage.setImageBitmap(BitmapFactory.decodeFile(coverFile.getAbsolutePath()));
            holder.coverImage.setVisibility(View.VISIBLE);
            holder.coverTitle.setText("");
        } else {
            if (holder.coverImage != null) {
                holder.coverImage.setImageDrawable(null);
                holder.coverImage.setVisibility(View.GONE);
            }
            holder.coverTitle.setText(buildCoverTitle(book.title));
        }
    }

    private static void bindFolderCover(FolderViewHolder holder, List<BookEntity> books) {
        TextView[] cells = new TextView[]{holder.coverOne, holder.coverTwo, holder.coverThree, holder.coverFour};
        for (int i = 0; i < cells.length; i++) {
            if (i < books.size()) {
                cells[i].setVisibility(View.VISIBLE);
                cells[i].setText(shortCoverText(books.get(i).title));
            } else {
                cells[i].setVisibility(View.INVISIBLE);
                cells[i].setText("");
            }
        }
    }

    private static boolean isFileMissing(BookEntity book) {
        if (book.storageDirPath == null || book.storageDirPath.trim().isEmpty()) {
            return false;
        }
        return !new File(book.storageDirPath).exists();
    }

    private static String buildCoverTitle(String title) {
        String clean = title == null ? "未命名" : title.trim();
        if (clean.isEmpty()) {
            clean = "未命名";
        }
        return clean.length() <= 18 ? clean : clean.substring(0, 18);
    }

    private static String shortCoverText(String title) {
        String clean = title == null ? "" : title.trim();
        if (clean.isEmpty()) {
            return "书";
        }
        return clean.length() <= 2 ? clean : clean.substring(0, 2);
    }

    private static String safeUpper(String value) {
        return value == null ? "" : value.toUpperCase(Locale.US);
    }

    static class BookViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView coverTitle;
        final TextView title;
        final TextView meta;
        final TextView progress;
        final View pinnedIndicator;
        final ImageView coverImage;
        final TextView statusBadge;

        BookViewHolder(@NonNull View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            coverTitle = itemView.findViewById(R.id.bookCoverTitle);
            title = itemView.findViewById(R.id.bookTitle);
            meta = itemView.findViewById(R.id.bookMeta);
            progress = itemView.findViewById(R.id.bookProgress);
            pinnedIndicator = itemView.findViewById(R.id.pinnedIndicator);
            coverImage = itemView.findViewById(R.id.bookCoverImage);
            statusBadge = itemView.findViewById(R.id.bookStatusBadge);
        }
    }

    static class FolderViewHolder extends RecyclerView.ViewHolder {
        final TextView badge;
        final TextView title;
        final TextView count;
        final TextView summary;
        final TextView coverOne;
        final TextView coverTwo;
        final TextView coverThree;
        final TextView coverFour;
        final ImageView pinnedIndicator;

        FolderViewHolder(@NonNull View itemView) {
            super(itemView);
            badge = itemView.findViewById(R.id.folderBadge);
            title = itemView.findViewById(R.id.folderTitle);
            count = itemView.findViewById(R.id.folderCount);
            summary = itemView.findViewById(R.id.folderSummary);
            coverOne = itemView.findViewById(R.id.folderCoverOne);
            coverTwo = itemView.findViewById(R.id.folderCoverTwo);
            coverThree = itemView.findViewById(R.id.folderCoverThree);
            coverFour = itemView.findViewById(R.id.folderCoverFour);
            pinnedIndicator = itemView.findViewById(R.id.folderPinnedIndicator);
        }
    }

    static class AddBookViewHolder extends RecyclerView.ViewHolder {
        AddBookViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
