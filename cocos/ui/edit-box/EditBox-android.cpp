/****************************************************************************
 Copyright (c) 2018 Xiamen Yaji Software Co., Ltd.

 http://www.cocos2d-x.org
 
 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:
 
 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.
 
 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 ****************************************************************************/

#include "cocos/bindings/event/EventDispatcher.h"
#include "platform/Application.h"
#include "EditBox.h"
#include "platform/android/jni/JniHelper.h"
#include "cocos/bindings/jswrapper/SeApi.h"
#include "cocos/bindings/manual/jsb_global.h"

#ifndef JCLS_EDITBOX
#define JCLS_EDITBOX  "com/cocos/lib/CocosEditBoxActivity"
#endif

#ifndef ORG_EDITBOX_CLASS_NAME
#define ORG_EDITBOX_CLASS_NAME com_cocos_lib_CocosEditBoxActivity
#endif
#define JNI_EDITBOX(FUNC) JNI_METHOD1(ORG_EDITBOX_CLASS_NAME,FUNC)

namespace
{
    se::Value textInputCallback;

    void getTextInputCallback()
    {
        if (! textInputCallback.isUndefined())
            return;

        auto global = se::ScriptEngine::getInstance()->getGlobalObject();
        se::Value jsbVal;
        if (global->getProperty("jsb", &jsbVal) && jsbVal.isObject())
        {
            jsbVal.toObject()->getProperty("onTextInput", &textInputCallback);
            // free globle se::Value before ScriptEngine clean up
            se::ScriptEngine::getInstance()->addBeforeCleanupHook([](){
                textInputCallback.setUndefined();
            });
        }
    }

    void callJSFunc(const std::string& type, const std::string& text)
    {
        getTextInputCallback();

        se::AutoHandleScope scope;
        se::ValueArray args;
        args.push_back(se::Value(type));
        args.push_back(se::Value(text));
        textInputCallback.toObject()->call(args, nullptr);
    }
}

namespace cc {

bool EditBox::_isShown = false;

void EditBox::show(const cc::EditBox::ShowInfo& showInfo)
{
    JniHelper::callStaticVoidMethod(JCLS_EDITBOX,
                                    "showNative",
                                    showInfo.defaultValue,
                                    showInfo.maxLength,
                                    showInfo.isMultiline,
                                    showInfo.confirmHold,
                                    showInfo.confirmType,
                                    showInfo.inputType);
    _isShown = true;
}

void EditBox::hide()
{
    JniHelper::callStaticVoidMethod(JCLS_EDITBOX, "hideNative");
    _isShown = false;
}

bool EditBox::complete()
{
    if (!_isShown)
        return false;

    EditBox::hide();

    return true;
}

}

extern "C" 
{
    JNIEXPORT void JNICALL JNI_EDITBOX(onKeyboardInputNative)(JNIEnv* env, jclass, jstring text)
    {
        auto textStr = cc::JniHelper::jstring2string(text);
            callJSFunc("input", textStr);

    }

    JNIEXPORT void JNICALL JNI_EDITBOX(onKeyboardCompleteNative)(JNIEnv* env, jclass, jstring text)
    {
        auto textStr = cc::JniHelper::jstring2string(text);
        callJSFunc("complete", textStr);
    }

    JNIEXPORT void JNICALL JNI_EDITBOX(onKeyboardConfirmNative)(JNIEnv* env, jclass, jstring text)
    {
        auto textStr = cc::JniHelper::jstring2string(text);
        callJSFunc("confirm", textStr);
    }
}
