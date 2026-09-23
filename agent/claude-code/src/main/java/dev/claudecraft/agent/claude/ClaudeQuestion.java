package dev.claudecraft.agent.claude;

import dev.claudecraft.agent.QuestionRequest;
import dev.claudecraft.agent.json.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ClaudeQuestion extends PendingRequest implements QuestionRequest {
    private final List<Question> questions;

    ClaudeQuestion(String id, Json request) {
        super(id, request);
        this.questions = parse(input().get("questions"));
    }

    @Override
    public List<Question> questions() {
        return questions;
    }

    @Override
    public void answer(List<String> answers) {
        Json byQuestion = Json.object();
        for (int i = 0; i < questions.size() && i < answers.size(); i++) byQuestion.put(questions.get(i).text(), answers.get(i));
        allowWith(input().copy().put("answers", byQuestion));
    }

    @Override
    public void dismiss() {
        deny("The user dismissed the questions without answering.");
    }

    private static List<Question> parse(Json questions) {
        List<Question> parsed = new ArrayList<>();
        for (Json question : questions.items()) {
            List<Option> options = new ArrayList<>();
            for (Json option : question.get("options").items()) {
                options.add(new Option(option.get("label").asString(""), option.get("description").asString("")));
            }
            parsed.add(new Question(
                question.get("header").asString(""),
                question.get("question").asString(""),
                question.get("multiSelect").asBoolean(false),
                Collections.unmodifiableList(options)));
        }
        return Collections.unmodifiableList(parsed);
    }
}
