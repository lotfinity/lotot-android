/*
 * Adapted from AndrOBD-libplugin PluginManager, revision 4ff67cd (GPLv3).
 * LotoT owns the host lifecycle; the upstream submodule remains unchanged.
 */
package com.fr3ts0n.androbd.plugin.mgr;

import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Switch;
import androidx.appcompat.app.AppCompatActivity;
import com.fr3ts0n.androbd.plugin.Plugin;
import com.fr3ts0n.androbd.plugin.R;

/** Legacy tools stay available explicitly, with an activity-scoped handler. */
public class LotoTPluginActivity extends AppCompatActivity implements Plugin.DataReceiver
{
    protected volatile PluginHandler pluginHandler;
    private ListView listView;

    public PluginHandler getActivePluginHandler() { return pluginHandler; }

    protected void setManagerView()
    {
        // LotoT's built-in gateway needs no external plugin discovery on resume.
        // Do not post callbacks against a shared static handler: an old activity
        // can otherwise clear it after the next activity has resumed.
        if (pluginHandler == null)
        {
            PluginHandler created = new PluginHandler(this);
            created.setDataReceiver(this);
            created.setup();
            pluginHandler = created;
        }
        setContentView(R.layout.content_main);
        listView = findViewById(android.R.id.list);
        listView.setAdapter(pluginHandler);
    }

    @Override protected void onDestroy()
    {
        if (pluginHandler != null)
        {
            pluginHandler.cleanup();
            pluginHandler = null;
        }
        super.onDestroy();
    }

    @Override public void setContentView(View view)
    {
        super.setContentView(view);
        ListView next = findViewById(android.R.id.list);
        if (next != null) listView = next;
    }

    public ListView getListView() { return listView; }
    public ListAdapter getListAdapter() { return listView.getAdapter(); }
    public void setListAdapter(ListAdapter adapter) { listView.setAdapter(adapter); }
    public void sendIdentify(View view)
    {
        if (pluginHandler != null) { pluginHandler.clear(); pluginHandler.identifyPlugins(); }
    }
    public void sendConfigure(View view)
    {
        if (pluginHandler != null) pluginHandler.triggerConfiguration(listView.getPositionForView(view));
    }
    public void sendPerformAction(View view)
    {
        if (pluginHandler != null) pluginHandler.triggerAction(listView.getPositionForView(view));
    }
    public void setPluginEnabled(View view)
    {
        if (pluginHandler != null) pluginHandler.setPluginEnabled(listView.getPositionForView(view), ((Switch)view).isChecked());
    }
    @Override public void onDataListUpdate(String csvString) { }
    @Override public void onDataUpdate(String key, String value) { }
}
