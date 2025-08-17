package org.aincraft.database;

import java.io.FileNotFoundException;
import java.io.InputStream;
import org.jetbrains.annotations.ApiStatus.Internal;

@Internal
interface ResourceExtractor {

  static InputStream getResourceStream(String filePath) throws FileNotFoundException {
    ClassLoader loader = ResourceExtractor.class.getClassLoader();
    InputStream resourceStream = loader.getResourceAsStream(filePath);
    if (resourceStream == null) {
      throw new FileNotFoundException(filePath);
    }
    return resourceStream;
  }
}
