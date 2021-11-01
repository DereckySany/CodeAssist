package com.tyron.psi.completions.lang.java;

import com.tyron.psi.completion.InsertionContext;
import com.tyron.psi.lookup.CommaTailType;
import com.tyron.psi.lookup.Lookup;
import com.tyron.psi.lookup.LookupElement;
import com.tyron.psi.lookup.LookupElementDecorator;
import com.tyron.psi.lookup.LookupItem;
import com.tyron.psi.lookup.TailTypeDecorator;
import com.tyron.psi.tailtype.TailType;
import com.tyron.psi.util.DocumentUtils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.com.intellij.lang.java.JavaLanguage;
import org.jetbrains.kotlin.com.intellij.pom.java.LanguageLevel;
import org.jetbrains.kotlin.com.intellij.psi.*;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.kotlin.com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.kotlin.com.intellij.util.containers.ContainerUtil;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
public class SmartCompletionDecorator extends LookupElementDecorator<LookupElement> {
    @NotNull private final Collection<? extends ExpectedTypeInfo> myExpectedTypeInfos;

    SmartCompletionDecorator(LookupElement item, @NotNull Collection<? extends ExpectedTypeInfo> expectedTypeInfos) {
        super(item);
        myExpectedTypeInfos = expectedTypeInfos;
    }

    @Nullable
    private TailType computeTailType(InsertionContext context) {
        if (context.getCompletionChar() == Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
            return TailType.NONE;
        }

        if (LookupItem.getDefaultTailType(context.getCompletionChar()) != null) {
            return null;
        }

        LookupElement delegate = getDelegate();
        LookupItem<?> item = as(LookupItem.CLASS_CONDITION_KEY);
        Object object = delegate.getObject();
//        if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET && (object instanceof PsiMethod || object instanceof PsiClass)) {
//            return TailType.NONE;
//        }

        PsiExpression enclosing =
                PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiExpression.class, false);

        if (enclosing != null) {
            final PsiType type = JavaCompletionUtil.getLookupElementType(delegate);
            final TailType itemType = item != null ? item.getTailType() : TailType.NONE;
            if (type != null && type.isValid()) {
                Set<TailType> voidTyped = new HashSet<>();
                Set<TailType> sameTyped = new HashSet<>();
                Set<TailType> assignableTyped = new HashSet<>();
                for (ExpectedTypeInfo info : myExpectedTypeInfos) {
                    final PsiType infoType = info.getType();
                    final PsiType originalInfoType = JavaCompletionUtil.originalize(infoType);
                    if (PsiType.VOID.equals(infoType)) {
                        voidTyped.add(info.getTailType());
                    } else if (infoType.equals(type) || originalInfoType.equals(type)) {
                        sameTyped.add(info.getTailType());
                    } else if ((info.getKind() == ExpectedTypeInfo.TYPE_OR_SUBTYPE &&
                            (infoType.isAssignableFrom(type) || originalInfoType.isAssignableFrom(type))) ||
                            (info.getKind() == ExpectedTypeInfo.TYPE_OR_SUPERTYPE &&
                                    (type.isAssignableFrom(infoType) || type.isAssignableFrom(originalInfoType)))) {
                        assignableTyped.add(info.getTailType());
                    }
                }

                if (!sameTyped.isEmpty()) {
                    return sameTyped.size() == 1 ? sameTyped.iterator().next() : itemType;
                }
                if (!assignableTyped.isEmpty()) {
                    return assignableTyped.size() == 1 ? assignableTyped.iterator().next() : itemType;
                }
                if (!voidTyped.isEmpty()) {
                    return voidTyped.size() == 1 ? voidTyped.iterator().next() : itemType;
                }

            }
            else {
                if (myExpectedTypeInfos.size() == 1) {
                    return myExpectedTypeInfos.iterator().next().getTailType();
                }
            }
            return itemType;
        }
        return null;
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context) {
        if (getObject() instanceof PsiVariable && context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR) {
            context.commitDocument();
            replaceMethodCallIfNeeded(context);
        }
        context.commitDocument();

        TailType tailType = computeTailType(context);

        LookupItem<?> lookupItem = getDelegate().as(LookupItem.CLASS_CONDITION_KEY);
        if (lookupItem != null && tailType != null) {
            lookupItem.setTailType(TailType.UNKNOWN);
        }
        TailTypeDecorator.withTail(getDelegate(), tailType).handleInsert(context);

        if (tailType == CommaTailType.INSTANCE) {
            //AutoPopupController.getInstance(context.getProject()).autoPopupParameterInfo(context.getEditor(), null);
        }
    }

    private static void replaceMethodCallIfNeeded(InsertionContext context) {
        PsiFile file = context.getFile();
        PsiElement element = file.findElementAt(context.getTailOffset());
        if (element instanceof PsiWhiteSpace &&
                (!element.textContains('\n') ||
                        false //CodeStyle.getLanguageSettings(file, JavaLanguage.INSTANCE).METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE
                )) {
            element = file.findElementAt(element.getTextRange().getEndOffset());
        }
        if (element != null && PsiUtilCore.getElementType(element) == JavaTokenType.LPARENTH && element.getParent() instanceof PsiExpressionList) {
            DocumentUtils.deleteString(context.getDocument(), context.getTailOffset(), element.getParent().getTextRange().getEndOffset());
        }
    }

    public static boolean hasUnboundTypeParams(final PsiMethod method, PsiType expectedType) {
        final PsiTypeParameter[] typeParameters = method.getTypeParameters();
        if (typeParameters.length == 0) return false;

        final Set<PsiTypeParameter> set = ContainerUtil.set(typeParameters);
        for (final PsiParameter parameter : method.getParameterList().getParameters()) {
            if (PsiTypesUtil.mentionsTypeParameters(parameter.getType(), set)) return false;
        }

        PsiSubstitutor substitutor = calculateMethodReturnTypeSubstitutor(method, expectedType);
        for (PsiTypeParameter parameter : typeParameters) {
            if (!TypeConversionUtil.typeParameterErasure(parameter).equals(substitutor.substitute(parameter))) {
                return true;
            }
        }

        return false;
    }

    public static PsiSubstitutor calculateMethodReturnTypeSubstitutor(@NotNull PsiMethod method, @NotNull final PsiType expected) {
        PsiType returnType = method.getReturnType();
        if (returnType == null) return PsiSubstitutor.EMPTY;

        PsiResolveHelper helper = JavaPsiFacade.getInstance(method.getProject()).getResolveHelper();
        return helper.inferTypeArguments(method.getTypeParameters(), new PsiType[]{expected}, new PsiType[]{returnType}, LanguageLevel.HIGHEST);
    }

}