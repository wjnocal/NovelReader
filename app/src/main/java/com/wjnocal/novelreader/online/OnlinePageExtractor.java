package com.wjnocal.novelreader.online;

import org.json.JSONObject;

/** Builds the in-page extractor used by both the browser and live online reader. */
public final class OnlinePageExtractor {
    private OnlinePageExtractor() {
    }

    public static String buildScript(OnlineBookSource source) {
        String titleSelector = source == null ? "" : plainSelector(source.chapter.title);
        String contentSelector = source == null ? "" : plainSelector(source.chapter.content);
        String filterPattern = source == null ? "" : source.chapter.filterTxt;
        return "(function(){try{" +
                "const customTitle=" + JSONObject.quote(titleSelector) + ";" +
                "const customContent=" + JSONObject.quote(contentSelector) + ";" +
                "const filterPattern=" + JSONObject.quote(filterPattern == null ? "" : filterPattern) + ";" +
                "const one=(s)=>{try{return s?document.querySelector(s):null}catch(e){return null}};" +
                "const clean=(v)=>(v||'').replace(/\\r/g,'').replace(/[ \\t]+\\n/g,'\\n').replace(/\\n[ \\t]+/g,'\\n').replace(/\\n{3,}/g,'\\n\\n').trim();" +
                "const domText=(root)=>{const node=root.cloneNode(true);node.querySelectorAll('br').forEach(e=>e.replaceWith(document.createTextNode('\\n')));node.querySelectorAll('p,dd,li,section,article,blockquote').forEach(e=>e.appendChild(document.createTextNode('\\n')));node.querySelectorAll('div').forEach(e=>{if(!e.querySelector('p,dd,li,section,article,blockquote'))e.appendChild(document.createTextNode('\\n'))});return node.textContent||''};" +
                "const paragraphs=(v)=>{let text=(v||'').replace(/(?:\\u00a0| ){4,}/g,'\\n').replace(/　{2,}/g,'\\n');const lines=text.split(/\\n+/).map(x=>x.replace(/^[ \\t　]+|[ \\t　]+$/g,'')).filter(Boolean);return lines.join('\\n\\n')};" +
                "const titleSelectors=[customTitle,'[itemprop=\"headline\"]','.chapter-title','.chapter_title','#chapter-name h1','#chapter-name h2','.bookname h1','.content h1','article h1','main h1','h1'];" +
                "let title='';for(const s of titleSelectors){const e=one(s);if(e&&clean(e.innerText).length>0){title=clean(e.innerText);break}}" +
                "const selectors=[customContent,'#content','#chaptercontent','#chapterContent','#htmlContent','#booktxt','#txtcontent','#txt','#Lab_Contents','.read-content','.chapter-content','.chapter_content','.row-detail','.yd_text2','.noveltext','article','main'];" +
                "let best=null,bestScore=-1;const seen=new Set();" +
                "for(const s of selectors){if(!s)continue;let list=[];try{list=[...document.querySelectorAll(s)]}catch(e){}for(const e of list){if(seen.has(e))continue;seen.add(e);const t=clean(e.innerText);if(t.length<40)continue;const links=clean([...e.querySelectorAll('a')].map(a=>a.innerText).join('')).length;const score=t.length-links*2+(t.match(/\\n/g)||[]).length*20;if(score>bestScore){best=e;bestScore=score}}}" +
                "if(!best){best=document.body}" +
                "const clone=best.cloneNode(true);clone.querySelectorAll('script,style,noscript,iframe,form,button,nav,.bottem2,.read-page,.page_chapter,.ads,.ad').forEach(e=>e.remove());" +
                "let content=clean(paragraphs(domText(clone)));" +
                "if(filterPattern){try{content=clean(content.replace(new RegExp(filterPattern,'g'),''))}catch(e){}}" +
                "const ad=/^(请记住|最新网址|手机用户|本章未完|一秒记住|天才一秒记住|喜欢本书|加入书签|投推荐票|返回目录|上一章|下一章|上一页|下一页).{0,80}$/;content=clean(paragraphs(content.split('\\n').filter(x=>!ad.test(clean(x))).join('\\n')));" +
                "const absolute=(a)=>{try{return a?new URL(a.getAttribute('href')||'',location.href).href:''}catch(e){return ''}};" +
                "const pick=(dir)=>{const isNext=dir==='next';const exact=isNext?/^(下一章|下章|下一回|后一章|后章|下一页|下页|next chapter|next page)$/i:/^(上一章|上章|上一回|前一章|前章|上一页|上页|previous chapter|previous page)$/i;const loose=isNext?/(下一章|下章|下一回|后一章|后章|下一页|下页)/:/(上一章|上章|上一回|前一章|前章|上一页|上页)/;let fallback='';for(const a of document.querySelectorAll('a[href]')){const text=clean(a.innerText||a.title||a.getAttribute('aria-label'));const id=((a.id||'')+' '+(a.className||'')+' '+(a.rel||'')).toLowerCase();const href=absolute(a);if(!href||href===location.href)continue;if(exact.test(text))return href;if(loose.test(text))fallback=fallback||href;const key=isNext?/(nextchapter|next-chapter|next_chapter|chapter-next|nextpage|next-page|next_page|pager_next|pb_next|link-next)/:/(prevchapter|previouschapter|prev-chapter|prev_chapter|chapter-prev|prevpage|previouspage|prev-page|prev_page|pager_prev|pb_prev|link-prev)/;if(key.test(id))fallback=fallback||href}return fallback};" +
                "const result={url:location.href,bookTitle:clean((one('meta[property=\"og:novel:book_name\"]')||{}).content)||clean((one('meta[property=\"og:title\"]')||{}).content)||clean(document.title).replace(/[_\\-—|｜].*$/,''),title:title||clean(document.title),content:content,previousUrl:pick('previous'),nextUrl:pick('next')};" +
                "NovelReaderBridge.onChapterExtracted(JSON.stringify(result));" +
                "}catch(e){NovelReaderBridge.onChapterError(String(e&&e.message?e.message:e));}})();";
    }

    private static String plainSelector(String rule) {
        if (rule == null) {
            return "";
        }
        int js = rule.indexOf("@js:");
        int java = rule.indexOf("@java:");
        int end = rule.length();
        if (js >= 0) {
            end = Math.min(end, js);
        }
        if (java >= 0) {
            end = Math.min(end, java);
        }
        String selector = rule.substring(0, end).trim();
        return selector.startsWith("/") ? "" : selector;
    }
}
