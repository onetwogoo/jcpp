package org.anarres.cpp;

import org.pcollections.Empty;
import org.pcollections.PSet;
import org.pcollections.PVector;
import org.pcollections.TreePVector;

import java.util.*;
import java.util.function.Function;

public class ActionCollector {
    /* Recording actions */
    public List<TokenS> original = new ArrayList<>();
    public List<Action> actions = new ArrayList<>();
    private List<TokenS> currentTokens = new ArrayList<>();
    private Preprocessor pp;
    private List<Source> rootSources;
    public Environment environment;

    public ActionCollector(Preprocessor pp, List<Source> rootSources) {
        this.pp = pp;
        this.rootSources = new ArrayList<>(rootSources);
        environment = pp.getCurrentState();
    }

    public void getToken(TokenS token, Source source) {
        if (token.token.getType() == Token.P_LINE || token.token.getType() == Token.EOF)
            return;
        if (pp.collectOnly && source instanceof MacroTokenSource) return;
        if (isRootSource(source)) {
            original.add(token);
        }
        currentTokens.add(token);
    }

    public void ungetToken(TokenS token, Source source) {
        if (token.token != currentTokens.get(currentTokens.size() - 1).token)
            throw new Error("Unget expected " + token + " got " + currentTokens.get(currentTokens.size() - 1).token);
        if (pp.collectOnly && source instanceof MacroTokenSource) return;
        if (isRootSource(source)) {
            original.remove(original.size() - 1);
        }
        currentTokens.remove(currentTokens.size() - 1);
    }

    private boolean isRootSource(Source source) {
        for (Source rootSource: rootSources) {
            if (rootSource == source) {
                return true;
            }
        }
        return false;
    }

    public int numToken() {
        return currentTokens.size();
    }

    public void directInsert(Action action) {
        if (pp.collectOnly) return;
        actions.add(action);
        environment = pp.getCurrentState();
    }

    public void revert(int index, Function<Action, Action> replaceFn) {
        if (pp.collectOnly) return;
        actions.set(index, replaceFn.apply(actions.get(index)));
    }

    /**
     * Delete all tokens in currentTokens except the last one
     * Then skip the last token
     */
    public void skipLast() {
        if (pp.collectOnly) return;
        if (currentTokens.isEmpty()) {
            throw new Error("skipLast when empty");
        }
        TokenS last = currentTokens.get(currentTokens.size() - 1);
        if (currentTokens.size() > 1) {
            currentTokens.remove(currentTokens.size() - 1);
            actions.add(new Replace(environment, TreePVector.from(currentTokens), Collections.<MapSeg>emptyList(), Empty.set()));
            environment = pp.getCurrentState();
        }
        actions.add(new Skip(environment, last));
        environment = pp.getCurrentState();
        currentTokens = new ArrayList<>();
    }

    /**
     * Delete all tokens in currentTokens
     * @return action index
     */
    public int delete() {
        if (pp.collectOnly) return -1;
        if (!currentTokens.isEmpty()) {
            actions.add(new Replace(environment, TreePVector.from(currentTokens), Collections.<MapSeg>emptyList(), Empty.set()));
            environment = pp.getCurrentState();

            currentTokens = new ArrayList<>();
            return actions.size() - 1;
        }
        return -1;
    }

    /**
     * Replace all tokens with newTokens, which may be filled later
     */
    public void replaceWithNewTokens(List<Token> newTokens, PSet<String> disables) {
        if (pp.collectOnly) return;
        actions.add(new Replace(environment, TreePVector.from(currentTokens), Collections.singletonList(
                new New(newTokens)
        ), disables));
        environment = pp.getCurrentState();
        currentTokens = new ArrayList<>();
    }

    /**
     * Replace all tokens with a mapping, which will be filled later
     */
    public void replaceWithMapping(List<MapSeg> mapping, PSet<String> disables) {
        if (pp.collectOnly) return;
        actions.add(new Replace(environment, TreePVector.from(currentTokens), mapping, disables));
        environment = pp.getCurrentState();
        currentTokens = new ArrayList<>();
    }
}
