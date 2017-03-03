package cn.gembit.transdev.file;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class FilePath {

    public final String pathString;
    private final char mSeparatorChar;

    private FilePath(String pathString, char separatorChar) {
        this.pathString = pathString;
        mSeparatorChar = separatorChar;
    }

    public FilePath(String absPath) {
        mSeparatorChar = absPath.charAt(0) == '/' ? '/' : '\\';

        int lenBeforeSep = absPath.indexOf(mSeparatorChar);
        lenBeforeSep = lenBeforeSep == -1 ? absPath.length() : lenBeforeSep;

        ArrayList<String> pathChain = new ArrayList<>();
        for (String str : absPath.substring(lenBeforeSep)
                .split(Pattern.quote(String.valueOf(mSeparatorChar)))) {
            if (str.equals("..")) {
                if (pathChain.size() > 0) {
                    pathChain.remove(pathChain.size() - 1);
                }
            } else if (!str.equals(".") && !str.isEmpty()) {
                pathChain.add(str);
            }
        }
        StringBuilder result = new StringBuilder();
        for (String s : pathChain) {
            result.append(mSeparatorChar).append(s);
        }
        if (result.length() == 0) {
            result.append(mSeparatorChar);
        }
        pathString = result.insert(0, absPath.substring(0, lenBeforeSep)).toString();
    }

    public boolean cover(FilePath descendant) {
        if (descendant == null ||
                !descendant.pathString.startsWith(pathString) ||
                descendant.mSeparatorChar != mSeparatorChar) {
            return false;
        } else {
            if (descendant.pathString.length() == pathString.length()) {
                return true;
            }
            if (pathString.charAt(pathString.length() - 1) == mSeparatorChar) {
                return descendant.pathString.charAt(pathString.length() - 1) == mSeparatorChar;
            } else {
                return descendant.pathString.charAt(pathString.length()) == mSeparatorChar;
            }
        }
    }

    public FilePath getRelative(String relative) {
        return new FilePath(pathString + mSeparatorChar + relative);
    }

    public FilePath getParent(int level) {
        StringBuilder builder = new StringBuilder();
        while (level-- > 0) {
            builder.append("..").append(mSeparatorChar);
        }
        return getRelative(builder.toString());
    }

    public FilePath getParent() {
        return getParent(1);
    }

    public FilePath getChild(String name) {
        if (name.equals(".")) {
            return this;
        } else if (name.equals("..")) {
            return getParent();
        } else {
            if (pathString.charAt(pathString.length() - 1) != mSeparatorChar) {
                return new FilePath(pathString + mSeparatorChar + name, mSeparatorChar);
            } else {
                return new FilePath(pathString + name, mSeparatorChar);
            }
        }
    }

    public List<String> getExtraDepthList(FilePath parent) {
        String str = parent == null ? pathString : pathString.substring(parent.pathString.length());
        ArrayList<String> list = new ArrayList<>();
        for (String s : str.split(Pattern.quote(String.valueOf(mSeparatorChar)))) {
            if (s != null && !s.isEmpty()) {
                list.add(s);
            }
        }
        return list;
    }

    public String getExtraDepthString(FilePath parent) {
        String str = parent == null ? pathString : pathString.substring(parent.pathString.length());
        return str.startsWith(String.valueOf(mSeparatorChar)) ? str.substring(1) : str;
    }

    public FilePath move(FilePath from, FilePath to) {
        if (!from.cover(this)) {
            return null;
        }
        StringBuilder builder = new StringBuilder(to.pathString);
        for (String s : pathString.substring(from.pathString.length())
                .split(Pattern.quote(String.valueOf(mSeparatorChar)))) {
            if (s != null && !s.isEmpty()) {
                if (builder.charAt(builder.length() - 1) == to.mSeparatorChar) {
                    builder.append(s);
                } else {
                    builder.append(to.mSeparatorChar).append(s);
                }
            }
        }
        return new FilePath(builder.toString(), mSeparatorChar);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof FilePath && pathString.equals(((FilePath) obj).pathString);
    }
}
