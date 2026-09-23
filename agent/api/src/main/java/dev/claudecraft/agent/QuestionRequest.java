package dev.claudecraft.agent;

import java.util.List;

public interface QuestionRequest {
    String id();

    List<Question> questions();

    void answer(List<String> answers);

    void dismiss();

    final class Question {
        private final String header;
        private final String text;
        private final boolean multiSelect;
        private final List<Option> options;

        public Question(String header, String text, boolean multiSelect, List<Option> options) {
            this.header = header;
            this.text = text;
            this.multiSelect = multiSelect;
            this.options = options;
        }

        public String header() {
            return header;
        }

        public String text() {
            return text;
        }

        public boolean multiSelect() {
            return multiSelect;
        }

        public List<Option> options() {
            return options;
        }
    }

    final class Option {
        private final String label;
        private final String description;

        public Option(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String label() {
            return label;
        }

        public String description() {
            return description;
        }
    }
}
