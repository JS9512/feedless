import {
  Decoration,
  DecorationSet,
  EditorView,
  PluginValue,
  ViewPlugin,
  ViewUpdate,
  WidgetType,
} from '@codemirror/view';
import { syntaxTree } from '@codemirror/language';

class InlineImageWidget extends WidgetType {
  constructor(private readonly url: string) {
    super();
  }

  override eq(other: InlineImageWidget) {
    return other.url == this.url;
  }

  override toDOM() {
    const wrap = document.createElement('div');
    wrap.setAttribute('aria-hidden', 'true');
    let image = wrap.appendChild(document.createElement('img'));
    image.src = this.url;
    image.className = 'cm-inline-image';
    return wrap;
  }

  override ignoreEvent() {
    return false;
  }
}

function inlineImages(view: EditorView) {
  let widgets: any[] = [];
  for (let { from, to } of view.visibleRanges) {
    syntaxTree(view.state).iterate({
      from,
      to,
      enter: (node) => {
        if (node.name == 'URL') {
          const url = view.state.doc.sliceString(node.from, node.to);
          const deco = Decoration.widget({
            widget: new InlineImageWidget(url),
            side: 1,
          });
          widgets.push(deco.range(node.to + 1));
        }
      },
    });
  }
  return Decoration.set(widgets);
}

export const inlineImagePlugin = ViewPlugin.fromClass(
  class implements PluginValue {
    decorations: DecorationSet;

    constructor(view: EditorView) {
      this.decorations = inlineImages(view);
    }

    update(update: ViewUpdate) {
      if (update.docChanged || update.viewportChanged) {
        this.decorations = inlineImages(update.view);
      }
    }
  },
  {
    decorations: (v) => v.decorations,

    // eventHandlers: {
    //   mousedown: (e, view) => {
    //     let target = e.target as HTMLElement
    //     if (target.nodeName == "INPUT" &&
    //       target.parentElement!.classList.contains("cm-boolean-toggle")) {
    //       return toggleBoolean(view, view.posAtDOM(target))
    //     }
    //     return false
    //   }
    // }
  },
);
