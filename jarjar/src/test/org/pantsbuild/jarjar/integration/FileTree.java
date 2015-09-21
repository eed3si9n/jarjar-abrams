package org.pantsbuild.jarjar.integration;

import java.io.File;
import java.util.Iterator;
import java.util.Stack;

/**
 * Iterator over a file tree.
 *
 * Roughly correlates to python's os.walk.
 */
public class FileTree implements Iterable<String> {

  private File root;

  /**
   * Creates a new recursive iterator over a directory.
   *
   * @param root The root directory to iterate over.
   */
  public FileTree(File root) {
    this.root = root;
  }

  public File getRoot() {
    return root;
  }

  public Iterator<String> iterator() {
    return new FileTreeIterator();
  }

  class FileTreeIterator implements Iterator<String> {

    private Stack<String> fileStack;

    public FileTreeIterator() {
      fileStack = new Stack<String>();
      expand("");
    }

    private void expand(String path) {
      File file = new File(root + File.separator + path);
      if (file.isDirectory()) {
        for (File f : file.listFiles()) {
          if (path.length() > 0) {
            fileStack.push(path + File.separator + f.getName());
          } else {
            fileStack.push(f.getName());
          }
        }
      }
    }

    @Override public boolean hasNext() {
      return !fileStack.isEmpty();
    }

    @Override public String next() {
      String nextPath = fileStack.pop();
      expand(nextPath);
      return nextPath;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("Remove not implemented.");
    }
  }
}