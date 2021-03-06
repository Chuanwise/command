package cn.chuanwise.command.format;

import cn.chuanwise.common.util.Arrays;
import cn.chuanwise.common.util.Preconditions;
import cn.chuanwise.common.util.Strings;

import java.util.*;
import java.util.regex.Pattern;

public class DefaultFormatCompiler
        implements FormatCompiler {

    public static final char SPACE = ' ';
    public static final char OR_OPERATOR = '|';
    public static final char DECLARE_PREFIX = '[';
    public static final char DECLARE_SUFFIX = ']';
    public static final char DEFAULT_OPERATOR = '?';
    public static final char OPTION_PREFIX = '-';
    public static final char OPTION_OPERATOR = '=';
    public static final char OPTIONAL_SUFFIX = '~';

    private static final DefaultFormatCompiler INSTANCE = new DefaultFormatCompiler();

    public static DefaultFormatCompiler getInstance() {
        return INSTANCE;
    }

    private DefaultFormatCompiler() {}

    @Override
    public FormatInfo compile(String text) {
        Preconditions.argument(Strings.nonEmpty(text), "格式字符串为空！");

        // 结尾添加一个空格方便编译
        final String originalText = text;
        text = text + ' ';
        final int length = text.length();

        CompileState state = CompileState.DEFAULT;
        final StringBuilder stringBuffer1 = new StringBuilder();
        final StringBuilder stringBuffer2 = new StringBuilder();
        final StringBuilder stringBuffer3 = new StringBuilder();

        final List<FormatElement> elementList = new ArrayList<FormatElement>();
        final Map<String, FormatElement> environment = new HashMap();

        for (int charIndex = 0; charIndex < length; charIndex++) {
            final char ch = text.charAt(charIndex);

            switch (state) {
                case DEFAULT: {
                    if (Character.isSpaceChar(ch)) {
                        continue;
                    }
                    check(ch != OR_OPERATOR, originalText, charIndex, "或运算符不能用于格式串元素开头！");
                    if (ch == DECLARE_PREFIX) {
                        state = CompileState.DECLARE;
                        continue;
                    } else {
                        stringBuffer1.append(ch);
                        state = CompileState.PLAIN_TEXT;
                        continue;
                    }
                }
                case PLAIN_TEXT: {
                    if (ch == SPACE) {
                        final String[] strings = stringBuffer1.toString().split(Pattern.quote("|"), 0);
                        stringBuffer1.setLength(0);
                        check(Arrays.nonEmpty(strings), originalText, charIndex, "解析器错误：普通文本为空！");

                        final List<String> stringList = new ArrayList<>(strings.length);
                        for (String string : strings) {
                            check(!stringList.contains(string), originalText, charIndex, "重复定义普通文本：" + string);
                            stringList.add(string);
                        }
                        final FormatElement element = new PlainTextsFormatElement(stringList);

                        check(elementList.isEmpty() || !(elementList.get(elementList.size() - 1) instanceof OptionalReferenceFormatElement),
                                originalText, charIndex,
                                "剩余参数后不能添加新的语法单元");
                        check(elementList.isEmpty() || !(elementList.get(elementList.size() - 1) instanceof OptionReferenceFormatElement),
                                originalText, charIndex,
                                "选项只能在最后的语法单元中出现");
                        elementList.add(element);

                        state = CompileState.DEFAULT;
                        continue;
                    } else {
                        stringBuffer1.append(ch);
                        continue;
                    }
                }
                case DECLARE: {
                    check(ch != DECLARE_SUFFIX, originalText, charIndex, "变量定义为空");
                    switch (ch) {
                        case DECLARE_SUFFIX: {
                            report(originalText, charIndex, "变量定义为空！");
                            continue;
                        }
                        case OPTION_PREFIX: {
                            state = CompileState.OPTION_NAME_DECLARE;
                            continue;
                        }
                        default: {
                            stringBuffer1.append(ch);
                            state = CompileState.SIMPLE_PARAMETER_DECLARE;
                            continue;
                        }
                    }
                }
                case OPTION_NAME_DECLARE: {
                    // stringBuffer1
                    switch (ch) {
                        case OPTION_OPERATOR: {
                            check(stringBuffer1.length() > 0, originalText, charIndex, "选项名不能为空！");
                            state = CompileState.OPTION_VALUES_DECLARE;
                            continue;
                        }
                        case DEFAULT_OPERATOR: {
                            check(stringBuffer1.length() > 0, originalText, charIndex, "选项名不能为空！");
                            state = CompileState.OPTION_DEFAULT_DECLARE;
                            continue;
                        }
                        case DECLARE_SUFFIX: {
                            final String names = stringBuffer1.toString();
                            check(stringBuffer1.length() > 0, originalText, charIndex, "选项名不能为空！");
                            stringBuffer1.setLength(0);
                            final Set<String> aliases = new HashSet<>();
                            final String[] strings = names.split(Pattern.quote("|"), 0);
                            for (String string : strings) {
                                check(Strings.nonEmpty(string), originalText, charIndex, "选项名不能为空！");
                                check(!environment.containsKey(string), originalText, charIndex, "参数 " + string + " 重定义！");
                                aliases.add(string);
                            }

                            // 前面已经断言过选项名非空了
                            final String name = strings[0];
                            final OptionReferenceFormatElement option = new OptionReferenceFormatElement(name, aliases, null, Collections.emptySet());
                            environment.put(name, option);

                            check(elementList.isEmpty() || !(elementList.get(elementList.size() - 1) instanceof OptionalReferenceFormatElement),
                                    originalText, charIndex,
                                    "剩余参数后不能添加新的语法单元");
                            elementList.add(option);

                            state = CompileState.DEFAULT;
                            continue;
                        }
                        default: {
                            stringBuffer1.append(ch);
                            continue;
                        }
                    }
                }
                case SIMPLE_PARAMETER_DECLARE: {
                    switch (ch) {
                        case DEFAULT_OPERATOR: {
                            state = CompileState.NULLABLE_PARAMETER_DECLARE;
                            continue;
                        }
                        case OPTIONAL_SUFFIX: {
                            final char nextChar = text.charAt(charIndex + 1);
                            check(nextChar == DECLARE_SUFFIX, originalText, charIndex, "剩余参数定义必须以 ~ 结尾");

                            final String name = stringBuffer1.toString();
                            stringBuffer1.setLength(0);
                            check(Strings.nonEmpty(name), originalText, charIndex, "参数名不能为空！");
                            check(!environment.containsKey(name), originalText, charIndex, "参数 " + name + " 重定义！");
                            final ReferenceFormatElement reference = new NonNullOptionalReferenceFormatElement(name);
                            environment.put(name, reference);

                            check(elementList.isEmpty() || !(elementList.get(elementList.size() - 1) instanceof OptionalReferenceFormatElement),
                                    originalText, charIndex,
                                    "剩余参数后不能添加新的语法单元");
                            check(elementList.isEmpty() || !(elementList.get(elementList.size() - 1) instanceof OptionReferenceFormatElement),
                                    originalText, charIndex,
                                    "选项只能在最后的语法单元中出现");
                            elementList.add(reference);

                            state = CompileState.DEFAULT;
                            charIndex++;
                            continue;
                        }
                        case DECLARE_SUFFIX: {
                            final String name = stringBuffer1.toString();
                            stringBuffer1.setLength(0);

                            check(Strings.nonEmpty(name), originalText, charIndex, "参数名不能为空！");
                            check(!environment.containsKey(name), originalText, charIndex, "参数 " + name + " 重定义！");
                            final ReferenceFormatElement reference = new RequiredReferenceFormatElement(name);
                            environment.put(name, reference);

                            check(elementList.isEmpty() || !(elementList.get(elementList.size() - 1) instanceof OptionalReferenceFormatElement),
                                    originalText, charIndex,
                                    "剩余参数后不能添加新的语法单元");
                            check(elementList.isEmpty() || !(elementList.get(elementList.size() - 1) instanceof OptionReferenceFormatElement),
                                    originalText, charIndex,
                                    "选项只能在最后的语法单元中出现");
                            elementList.add(reference);

                            state = CompileState.DEFAULT;
                            continue;
                        }
                        default: {
                            stringBuffer1.append(ch);
                            continue;
                        }
                    }
                }
                case OPTION_VALUES_DECLARE: {
                    // stringBuffer2
                    switch (ch) {
                        case DECLARE_SUFFIX: {
                            final String optionalString = stringBuffer2.toString();
                            stringBuffer2.setLength(0);
                            final Set<String> optionalValues = new HashSet<>();
                            if (Strings.nonEmpty(optionalString)) {
                                final String[] strings = optionalString.split(Pattern.quote("|"), 0);
                                for (String string : strings) {
                                    check(!optionalValues.contains(string), originalText, charIndex, "选项值重定义：" + string);
                                    optionalValues.add(string);
                                }
                            }

                            final String names = stringBuffer1.toString();
                            stringBuffer1.setLength(0);
                            final Set<String> aliases = new HashSet<>();
                            final String[] strings = names.split(Pattern.quote("|"), 0);
                            for (String string : strings) {
                                check(Strings.nonEmpty(string), originalText, charIndex, "选项名不能为空！");
                                aliases.add(string);
                            }

                            // 前面已经断言过选项名非空了
                            final String name = strings[0];
                            check(!environment.containsKey(name), originalText, charIndex, "参数 " + name + " 重定义！");
                            final OptionReferenceFormatElement option = new OptionReferenceFormatElement(name, aliases, null, optionalValues);
                            environment.put(name, option);

                            check(elementList.isEmpty() || !(elementList.get(elementList.size() - 1) instanceof OptionalReferenceFormatElement),
                                    originalText, charIndex,
                                    "剩余参数后不能添加新的语法单元");
                            elementList.add(option);

                            state = CompileState.DEFAULT;
                            continue;
                        }
                        case DEFAULT_OPERATOR: {
                            check(stringBuffer2.length() > 0, originalText, charIndex, "选项可选值为空，不应使用 =");
                            state = CompileState.OPTION_DEFAULT_DECLARE;
                            continue;
                        }
                        default: {
                            stringBuffer2.append(ch);
                            continue;
                        }
                    }
                }
                case NULLABLE_PARAMETER_DECLARE: {
                    switch (ch) {
                        case DECLARE_SUFFIX: {
                            report(originalText, charIndex, "带有默认值的参数，只能是选项或剩余参数！");
                            continue;
                        }
                        case OPTIONAL_SUFFIX: {
                            // 默认值可以为空
                            final String defaultValue = stringBuffer2.toString();
                            stringBuffer2.setLength(0);

                            final String name = stringBuffer1.toString();
                            check(Strings.nonEmpty(name), originalText, charIndex, "参数名不能为空！");
                            check(!environment.containsKey(name), originalText, charIndex, "参数 " + name + " 重定义！");
                            check(charIndex + 1 < text.length() || text.charAt(charIndex + 1) == ']', originalText, charIndex, "可空剩余参数未以 ] 结尾！");

                            charIndex++;
                            final ReferenceFormatElement reference = new NullableOptionalReferenceFormatElement(name, defaultValue);
                            environment.put(name, reference);

                            check(elementList.isEmpty() || !(elementList.get(elementList.size() - 1) instanceof OptionalReferenceFormatElement),
                                    originalText, charIndex,
                                    "剩余参数后不能添加新的语法单元");
                            elementList.add(reference);

                            state = CompileState.DEFAULT;
                            continue;
                        }
                        default: {
                            stringBuffer2.append(ch);
                            continue;
                        }
                    }
                }
                case OPTION_DEFAULT_DECLARE: {
                    // stringBuffer3
                    switch (ch) {
                        case DECLARE_SUFFIX: {
                            // 选项名
                            final String names = stringBuffer1.toString();
                            stringBuffer1.setLength(0);
                            final Set<String> aliases = new HashSet<>();
                            final String[] strings = names.split(Pattern.quote("|"), 0);
                            for (String string : strings) {
                                check(Strings.nonEmpty(string), originalText, charIndex, "选项名不能为空！");
                                check(!environment.containsKey(string), originalText, charIndex, "选项重定义：" + string);
                                aliases.add(string);
                            }

                            // 选项值，有可能没有
                            final Set<String> optionalValues;
                            if (stringBuffer2.length() > 0) {
                                final String optionalString = stringBuffer2.toString();
                                stringBuffer2.setLength(0);
                                optionalValues = new HashSet<>();
                                for (String string : optionalString.split(Pattern.quote("|"), 0)) {
                                    check(Strings.nonEmpty(string), originalText, charIndex, "选项名不能为空！");
                                    check(!optionalValues.contains(string), originalText, charIndex, "选项重定义：" + string);
                                    optionalValues.add(string);
                                }
                            } else {
                                optionalValues = new HashSet<>();
                            }

                            // 默认值可以为空
                            final String defaultValue = stringBuffer3.toString();
                            stringBuffer3.setLength(0);
                            if (Strings.nonEmpty(defaultValue)) {
                                optionalValues.add(defaultValue);
                            }

                            final String name = strings[0];
                            final OptionReferenceFormatElement option = new OptionReferenceFormatElement(name, aliases, defaultValue, optionalValues);
                            environment.put(name, option);

                            check(elementList.isEmpty() || !(elementList.get(elementList.size() - 1) instanceof OptionalReferenceFormatElement),
                                    originalText, charIndex,
                                    "剩余参数后不能添加新的语法单元");
                            elementList.add(option);

                            state = CompileState.DEFAULT;
                            continue;
                        }
                        default: {
                            stringBuffer3.append(ch);
                            continue;
                        }
                    }
                }
                default:
                    report(originalText, charIndex, "解析器错误：未知状态：" + state);
            }
        }

        check(state == CompileState.DEFAULT, originalText, originalText.length() - 1, "格式串不应在此结尾");

        return new FormatInfo(originalText, elementList.toArray(new FormatElement[0]));
    }

    private void check(boolean legal, String text, int charIndex, String message) {
        if (!legal) {
            report(text, charIndex, message);
        }
    }

    private void report(String text, int charIndex, String message) {
        throw new IllegalArgumentException("编译错误：" + message + "（位于第 " + (charIndex + 1) + " 个字符附近）\n"
                + text + "\n" + Strings.repeat(" ", Math.max(0, charIndex - 1)) + "~~~");
    }

    private enum CompileState {
        DEFAULT,
        PLAIN_TEXT,
        DECLARE,
        SIMPLE_PARAMETER_DECLARE,
        NULLABLE_PARAMETER_DECLARE,
        OPTION_NAME_DECLARE,
        OPTION_VALUES_DECLARE,
        OPTION_DEFAULT_DECLARE,
    }
}
