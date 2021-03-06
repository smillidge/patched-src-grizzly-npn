/*
 * Copyright (c) 2006, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package sun.security.ssl;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import javax.net.ssl.*;

/**
 * This file contains all the classes relevant to TLS Extensions for the
 * ClientHello and ServerHello messages. The extension mechanism and
 * several extensions are defined in RFC 3546. Additional extensions are
 * defined in the ECC RFC 4492.
 *
 * Currently, only the two ECC extensions are fully supported.
 *
 * The classes contained in this file are:
 *  . HelloExtensions: a List of extensions as used in the client hello
 *      and server hello messages.
 *  . ExtensionType: an enum style class for the extension type
 *  . HelloExtension: abstract base class for all extensions. All subclasses
 *      must be immutable.
 *
 *  . UnknownExtension: used to represent all parsed extensions that we do not
 *      explicitly support.
 *  . ServerNameExtension: the server_name extension.
 *  . SignatureAlgorithmsExtension: the signature_algorithms extension.
 *  . SupportedEllipticCurvesExtension: the ECC supported curves extension.
 *  . SupportedEllipticPointFormatsExtension: the ECC supported point formats
 *      (compressed/uncompressed) extension.
 *
 * @since   1.6
 * @author  Andreas Sterbenz
 */
final class HelloExtensions {

    private List<HelloExtension> extensions;
    private int encodedLength;

    HelloExtensions() {
        extensions = Collections.emptyList();
    }

    HelloExtensions(HandshakeInStream s) throws IOException {
        int len = s.getInt16();
        extensions = new ArrayList<HelloExtension>();
        encodedLength = len + 2;
        while (len > 0) {
            int type = s.getInt16();
            int extlen = s.getInt16();
            ExtensionType extType = ExtensionType.get(type);
            HelloExtension extension;
            if (extType == ExtensionType.EXT_SERVER_NAME) {
                extension = new ServerNameExtension(s, extlen);
            } else if (extType == ExtensionType.EXT_SIGNATURE_ALGORITHMS) {
                extension = new SignatureAlgorithmsExtension(s, extlen);
            } else if (extType == ExtensionType.EXT_ELLIPTIC_CURVES) {
                extension = new SupportedEllipticCurvesExtension(s, extlen);
            } else if (extType == ExtensionType.EXT_EC_POINT_FORMATS) {
                extension =
                        new SupportedEllipticPointFormatsExtension(s, extlen);
            } else if (extType == ExtensionType.EXT_RENEGOTIATION_INFO) {
                extension = new RenegotiationInfoExtension(s, extlen);
            // BEGIN GRIZZLY NPN
            } else if (extType == ExtensionType.EXT_NEXT_PROTOCOL_NEGOTIATION) {
                extension = NextProtocolNegotiationExtension.builder().handshakeIn(s, extlen).build();
            } else if (extType == ExtensionType.EXT_APPLICATION_LEVEL_PROTOCOL_NEGOTIATION) {
                                extension = AlpnExtension.builder().handshakeIn(s, extlen).build();
            // END GRIZZLY NPN
            } else {
                extension = new UnknownExtension(s, extlen, extType);
            }
            extensions.add(extension);
            len -= extlen + 4;
        }
        if (len != 0) {
            throw new SSLProtocolException(
                        "Error parsing extensions: extra data");
        }
    }

    // Return the List of extensions. Must not be modified by the caller.
    List<HelloExtension> list() {
        return extensions;
    }

    void add(HelloExtension ext) {
        if (extensions.isEmpty()) {
            extensions = new ArrayList<HelloExtension>();
        }
        extensions.add(ext);
        encodedLength = -1;
    }

    HelloExtension get(ExtensionType type) {
        for (HelloExtension ext : extensions) {
            if (ext.type == type) {
                return ext;
            }
        }
        return null;
    }

    int length() {
        if (encodedLength >= 0) {
            return encodedLength;
        }
        if (extensions.isEmpty()) {
            encodedLength = 0;
        } else {
            encodedLength = 2;
            for (HelloExtension ext : extensions) {
                encodedLength += ext.length();
            }
        }
        return encodedLength;
    }

    void send(HandshakeOutStream s) throws IOException {
        int length = length();
        if (length == 0) {
            return;
        }
        s.putInt16(length - 2);
        for (HelloExtension ext : extensions) {
            ext.send(s);
        }
    }

    void print(PrintStream s) throws IOException {
        for (HelloExtension ext : extensions) {
            s.println(ext.toString());
        }
    }
}
