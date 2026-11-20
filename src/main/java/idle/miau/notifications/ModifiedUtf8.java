package idle.miau.notifications;

final class ModifiedUtf8 {
    static final int NATIVE_MESSAGE_BYTES = 100;

    private ModifiedUtf8() {
    }

    static byte[] encodeCString(String value) {
        if (value == null) {
            throw new NullPointerException("value");
        }

        byte[] encoded = new byte[NATIVE_MESSAGE_BYTES + 1];
        int output = 0;
        outer:
        for (int index = 0; index < value.length(); index++) {
            int character = value.charAt(index);
            int[] bytes;
            if (character == 0) {
                bytes = new int[]{0xC0, 0x80};
            } else if (character <= 0x7F) {
                bytes = new int[]{character};
            } else if (character <= 0x7FF) {
                bytes = new int[]{
                        0xC0 | character >> 6,
                        0x80 | character & 0x3F
                };
            } else {
                bytes = new int[]{
                        0xE0 | character >> 12,
                        0x80 | character >> 6 & 0x3F,
                        0x80 | character & 0x3F
                };
            }

            for (int next : bytes) {
                if (output == NATIVE_MESSAGE_BYTES) {
                    break outer;
                }
                encoded[output++] = (byte) next;
            }
        }

        byte[] result = new byte[output + 1];
        System.arraycopy(encoded, 0, result, 0, output);
        return result;
    }
}
