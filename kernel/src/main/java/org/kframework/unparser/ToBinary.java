// Copyright (c) 2015-2016 K Team. All Rights Reserved.
package org.kframework.unparser;

import org.kframework.kore.InjectedKLabel;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KRewrite;
import org.kframework.kore.KSequence;
import org.kframework.kore.KToken;
import org.kframework.kore.KVariable;
import org.kframework.parser.binary.BinaryParser;
import org.kframework.utils.errorsystem.KEMException;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by dwightguth on 11/16/15.
 */
public class ToBinary {

    private ToBinary() {
    }

    public static void apply(OutputStream out, K k) {
        try {
            DataOutputStream data = new DataOutputStream(out);
            //magic
            data.writeByte(0x7f);
            data.writeBytes("KAST");
            //version
            data.writeByte(4);
            data.writeByte(0);
            data.writeByte(0);
            new Traverse(data).apply(k);
            data.writeByte(BinaryParser.END);
        } catch (IOException e) {
            throw KEMException.criticalError("Could not write K term to binary", e, k);
        }

    }

    public static byte[] apply(K k) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        apply(out, k);
        return out.toByteArray();
    }

    private static class Traverse {
        DataOutputStream data;
        Map<String, Integer> interns = new HashMap<>();

        public Traverse(DataOutputStream data) {
            this.data = data;
        }

        void apply(K k) throws IOException {
            if (k instanceof KToken) {
                KToken tok = (KToken) k;

                data.writeByte(BinaryParser.KTOKEN);
                writeString(tok.s());
                writeString(tok.sort().name());

            } else if (k instanceof KApply) {
                KApply app = (KApply) k;

                for (K item : app.asIterable()) {
                    apply(item);
                }
                data.writeByte(BinaryParser.KAPPLY);
                writeString(app.klabel().name());
                data.writeBoolean(app.klabel() instanceof KVariable);
                data.writeInt(app.size());

            } else if (k instanceof KSequence) {
                KSequence seq = (KSequence) k;

                for (K item : seq.asIterable()) {
                    apply(item);
                }
                data.writeByte(BinaryParser.KSEQUENCE);
                data.writeInt(seq.size());

            } else if (k instanceof KVariable) {
                KVariable var = (KVariable) k;

                data.writeByte(BinaryParser.KVARIABLE);
                writeString(var.name());

            } else if (k instanceof KRewrite) {
                KRewrite rew = (KRewrite) k;

                apply(rew.left());
                apply(rew.right());
                data.writeByte(BinaryParser.KREWRITE);

            } else if (k instanceof InjectedKLabel) {
                InjectedKLabel inj = (InjectedKLabel) k;

                data.writeByte(BinaryParser.INJECTEDKLABEL);
                writeString(inj.klabel().name());
                data.writeBoolean(inj.klabel() instanceof KVariable);

            }
        }

        private void writeString(String s) throws IOException {
            int idx = interns.getOrDefault(s, interns.size());
            data.writeInt(interns.size() - idx);
            if (idx == interns.size()) {
                data.writeInt(s.length());
                data.writeChars(s);
                interns.put(s, interns.size());
            }
        }
    }
}
