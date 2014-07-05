/*
 * Copyright (C) 2014 B3Partners B.V.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nl.opengeogroep.filesetsync;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import static nl.opengeogroep.filesetsync.FileRecord.TYPE_DIRECTORY;

/**
 * Given a list of FileRecords which was built by depth-first traversing
 * directories (no requirement that directories in directories are traversed first
 * before files in the directory), return sublists of FileRecords for a single
 * directory (only direct subdirectories and files in the directory) with the
 * directory as the first element. Used by the client to find files and
 * directories to delete in local directories.
 * <p>
 * <h3>Algorithm</h3>
 * Returns directories breadth-first. Let the full file list be:
 * <pre>
 * d .
 * f file1
 * d dir1
 * d dir1/subdir
 * f dir1/file2
 * f file2
 * d dir2
 * f dir2/file3
 * </pre>
 * First return a list of all files and directories in . directory by iterating
 * through the full list as follows:
 * <pre>
 * add  d .
 * add  f file1
 * add  d dir1, add index to deque, skip all following entries starting with dir1
 * skip d dir1/subdir
 * skip f dir1/file2
 * add  f file2
 * add  d dir2, add index to deque, skip all following entries starting with dir2
 * skip f dir2/file3
 * </pre>
 * The hasNext() method returns true until the deque is empty. For the next
 * returned list, process the first skipped subdirectory by starting from the
 * index popped from the deque:
 * <pre>
 * add     d dir1
 * add     d dir1/subdir, add index to deque, skip all following entries starting with dir1/subdir
 * add     f dir1/file2
 * stop at f file2, because it did not start with dir1
 * </pre>
 * <p>
 * @author Matthijs Laan <matthijslaan@b3partners.nl>
 */
public class FileRecordListDirectoryIterator implements Iterable<List<FileRecord>>, Iterator<List<FileRecord>> {

    private final List<FileRecord> list;

    private final ArrayDeque<Integer> deque = new ArrayDeque(Arrays.asList(new Integer[] { 0 }));

    private List<FileRecord> next;

    public FileRecordListDirectoryIterator(List<FileRecord> list) {
        this.list = list;
    }

    private String dequeToString() {
        List<String> names = new ArrayList(deque.size());
        for(Integer i: deque) {
            names.add(list.get(i).getName());
        }
        return names.toString();
    }

    @Override
    public boolean hasNext() {
        if(next != null) {
            return true;
        }

        if(deque.isEmpty()) {
            return false;
        }

        int index = deque.removeFirst();
        int startIndex = index;
        FileRecord dirRecord = list.get(index);

        next = new ArrayList();
        next.add(dirRecord);

        String currentDirName = dirRecord.getName();
        System.out.println("Current directory: " + dirRecord);
        for(index++; index < list.size(); index++) {
            FileRecord fr = list.get(index);

            // Is record in current directory?
            if(startIndex != 0 && !fr.getName().startsWith(currentDirName)) {
                System.out.println("Went outside current directory: " + fr.getName());
                break;
            }

            // Look at the path below current dir and / character
            String subDirPart = startIndex == 0 ? fr.getName() : fr.getName().substring(currentDirName.length()+1);

            // Only add direct subdirectories to the deque and next list and
            // only files in currentDir to the next list
            if(!subDirPart.contains("/")) {
                next.add(fr);
                if(fr.getType() == TYPE_DIRECTORY) {
                    // breadth-first
                    deque.addLast(index);
                    System.out.println("Added direct subdirectory to next list and deque: " + dequeToString());
                } else {
                    System.out.println("Added file: " + fr.getName());
                }
            } else {
                System.out.println("Skipping non-direct file/directory: " + fr.getName());
            }
        }
        return true;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<FileRecord> next() {
        if(!hasNext()) {
            throw new NoSuchElementException();
        }

        List<FileRecord> sublist = next;
        next = null;
        return sublist;
    }

    @Override
    public Iterator<List<FileRecord>> iterator() {
        return this;
    }
}
