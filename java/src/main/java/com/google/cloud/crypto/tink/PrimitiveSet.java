// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.cloud.crypto.tink;

import com.google.cloud.crypto.tink.TinkProto.Keyset;
import com.google.cloud.crypto.tink.TinkProto.Keyset.Key;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.security.GeneralSecurityException;

/**
 * A container class for a set of primitives (i.e. implementations of cryptographic
 * primitives offered by Tink).  It provides also additional properties for the primitives
 * it holds.  In particular, one of the primitives in the set can be distinguished as
 * "the primary" one. <p>
 *
 * PrimitiveSet is an auxiliary class used for supporting key rotation: primitives in a set
 * correspond to keys in a keyset.  Users will usually work with primitive instances,
 * which essentially wrap primitive sets.  For example an instance of an Aead-primitive
 * for a given keyset holds a set of Aead-primitivies corresponding to the keys in the keyset,
 * and uses the set members to do the actual crypto operations: to encrypt data the primary
 * Aead-primitive from the set is used, and upon decryption the ciphertext's prefix
 * determines the id of the primitive from the set. <p>
 *
 * PrimitiveSet is a public class to allow its use in implementations of custom primitives.
 */
public class PrimitiveSet<P> {
  /**
   * A single entry in the set. In addition to the actual primitive it holds also
   * some extra information about the primitive.
   */
  public class Entry<P> {
    // The actual primitive.
    private final P primitive;
    // Identifies the primitive within the set.
    // It is the ciphertext prefix of the correponding key.
    private final byte[] identifier;
    // The status of the key represented by the primitive.
    private final Key.StatusType status;

    public Entry(P primitive, byte[] identifier, Key.StatusType status) {
      this.primitive = primitive;
      this.identifier = identifier;
      this.status = status;
    }
    public P getPrimitive() {
      return this.primitive;
    }
    public Key.StatusType getStatus() {
      return status;
    }
    public final byte[] getIdentifier() {
      return identifier;
    }
  }

  /**
   * @returns the entry with the primary primitive.
   */
  public Entry<P> getPrimary() {
    return primary;
  }

  /**
   * @returns all primitives using RAW prefix.
   */
  public List<Entry<P>> getRawPrimitives() {
    return getPrimitive(CryptoFormat.RAW_PREFIX);
  }

  /**
   * @returns the entries with primitive identifed by {@code identifier}.
   */
  public List<Entry<P>> getPrimitive(byte[] identifier) {
    List<Entry<P>> found = primitives.get(new String(identifier, StandardCharsets.UTF_8));
    return found != null ? found : Collections.<Entry<P>>emptyList();
  }

  /**
   * The primitives are stored in a hash map of (ciphertext prefix, list of primivies sharing
   * the prefix).
   * This allows quickly retrieving the list of primitives sharing some particular prefix.
   * Because all RAW keys are using an empty prefix, this also quickly allows retrieving them.
   */
  private ConcurrentMap<java.lang.String, List<Entry<P>>> primitives =
    new ConcurrentHashMap<java.lang.String, List<Entry<P>>>();

  private Entry<P> primary;

  protected static <P> PrimitiveSet<P> newPrimitiveSet() {
    return new PrimitiveSet<P>();
  }

  /**
   * @returns the entries with primitives identified by the ciphertext prefix of {@code key}.
   */
  protected List<Entry<P>> getPrimitive(Keyset.Key key)
      throws GeneralSecurityException {
    return getPrimitive(CryptoFormat.getPrefix(key));
  }

  /**
   * @returns sets given Entry {@code primary} as the primary one.
   */
  protected void setPrimary(Entry<P> primary) {
    this.primary = primary;
  }

  /**
    * Creates an entry in the primitive table.
    * @returns the added entry
    */
  protected Entry<P> addPrimitive(P primitive, Keyset.Key key) throws GeneralSecurityException {
    Entry<P> entry = new Entry<P>(primitive, CryptoFormat.getPrefix(key), key.getStatus());
    List<Entry<P>> list = new ArrayList<Entry<P>>();
    list.add(entry);
    // Cannot use [] as keys in hash map, convert to string.
    String identifier = new String(entry.getIdentifier(), StandardCharsets.UTF_8);
    List<Entry<P>> existing = primitives.put(identifier, Collections.unmodifiableList(list));
    if (existing != null) {
      List<Entry<P>> newList = new ArrayList<Entry<P>>();
      newList.addAll(existing);
      newList.add(entry);
      primitives.put(identifier, Collections.unmodifiableList(newList));
    }
    return entry;
  }
}
