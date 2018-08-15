package com.nfcbluetoothapp.nfcbluetoothapp;

/*
*
* This file is licensed under The Code Project Open License (CPOL) 1.02
* http://www.codeproject.com/info/cpol10.aspx
* http://www.codeproject.com/info/CPOL.zip
*
* License Preamble:
* This License governs Your use of the Work. This License is intended to allow developers to use the Source
* Code and Executable Files provided as part of the Work in any application in any form.
*
* The main points subject to the terms of the License are:
*    Source Code and Executable Files can be used in commercial applications;
*    Source Code and Executable Files can be redistributed; and
*    Source Code can be modified to create derivative works.
*    No claim of suitability, guarantee, or any warranty whatsoever is provided. The software is provided "as-is".
*    The Article(s) accompanying the Work may not be distributed or republished without the Author's consent
*
* This License is entered between You, the individual or other entity reading or otherwise making use of
* the Work licensed pursuant to this License and the individual or other entity which offers the Work
* under the terms of this License ("Author").
*  (See Links above for full license text)
*/

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Color;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

class FileChooser
{
    private String m_sdcardDirectory = "";
    private Context m_context;
    private TextView m_titleView;
    private String Default_File_Name = "default.txt";
    private String Selected_File_Name = Default_File_Name;
    private EditText input_text;

    private String m_dir = "";
    private List<String> m_subdirs = null;
    private SimpleFileDialogListener m_SimpleFileDialogListener = null;
    private ArrayAdapter<String> m_listAdapter = null;

    interface SimpleFileDialogListener
    {
        void onChosenDir(String chosenDir);
    }

    FileChooser(Context context, SimpleFileDialogListener SimpleFileDialogListener)
    {
        m_context = context;
        m_sdcardDirectory = Environment.getExternalStorageDirectory().getAbsolutePath();
        m_SimpleFileDialogListener = SimpleFileDialogListener;

        try
        {
            m_sdcardDirectory = new File(m_sdcardDirectory).getCanonicalPath();
        }
        catch (IOException ioe){}
    }

    void chooseFile_or_Dir()
    {
        if (m_dir.equals(""))	chooseFile_or_Dir(m_sdcardDirectory);
        else chooseFile_or_Dir(m_dir);
    }

    private void chooseFile_or_Dir(String dir)
    {
        File dirFile = new File(dir);
        if (! dirFile.exists() || ! dirFile.isDirectory())
        {
            dir = m_sdcardDirectory;
        }

        try
        {
            dir = new File(dir).getCanonicalPath();
        }
        catch (IOException ioe)
        {
            return;
        }

        m_dir = dir;
        m_subdirs = getDirectories(dir);

        class SimpleFileDialogOnClickListener implements DialogInterface.OnClickListener
        {
            public void onClick(DialogInterface dialog, int item)
            {
                String m_dir_old = m_dir;
                String sel = "" + ((AlertDialog) dialog).getListView().getAdapter().getItem(item);
                if (sel.charAt(sel.length()-1) == '/')	sel = sel.substring(0, sel.length()-1);

                if (sel.equals(".."))
                {
                    m_dir = m_dir.substring(0, m_dir.lastIndexOf("/"));
                }
                else
                {
                    m_dir += "/" + sel;
                }
                Selected_File_Name = Default_File_Name;

                if ((new File(m_dir).isFile())) // If the selection is a regular file
                {
                    m_dir = m_dir_old;
                    Selected_File_Name = sel;
                }
                updateDirectory();
            }
        }

        AlertDialog.Builder dialogBuilder = createDirectoryChooserDialog(dir, m_subdirs,
                new SimpleFileDialogOnClickListener());

        dialogBuilder.setPositiveButton("OK", new OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                if (m_SimpleFileDialogListener != null){

                    Selected_File_Name= input_text.getText() +"";
                    m_SimpleFileDialogListener.onChosenDir(m_dir + "/" + Selected_File_Name);
                }
            }
        }).setNegativeButton("Anuluj", null);
        final AlertDialog dirsDialog = dialogBuilder.create();
        dirsDialog.show();
    }

    private List<String> getDirectories(String dir)
    {
        List<String> dirs = new ArrayList<>();
        try
        {
            File dirFile = new File(dir);
            if (! m_dir.equals(m_sdcardDirectory) ) dirs.add("..");
            if (! dirFile.exists() || ! dirFile.isDirectory())
                return dirs;

            for (File file : dirFile.listFiles())
                dirs.add( file.getName() );
        }
        catch (Exception e) {}

        Collections.sort(dirs, new Comparator<String>()
        {
            public int compare(String o1, String o2)
            {
                return o1.compareTo(o2);
            }
        });
        return dirs;
    }

    private AlertDialog.Builder createDirectoryChooserDialog(String title, List<String> listItems,
                                                             DialogInterface.OnClickListener onClickListener)
    {
        TextView m_titleView1;
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(m_context);
        m_titleView1 = new TextView(m_context);
        m_titleView1.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        m_titleView1.setText("Wybierz:");
        m_titleView1.setGravity(Gravity.CENTER_VERTICAL);
        m_titleView1.setBackgroundColor(Color.GRAY);
        m_titleView1.setTextColor(Color.WHITE);
        LinearLayout titleLayout1 = new LinearLayout(m_context);
        titleLayout1.setOrientation(LinearLayout.VERTICAL);
        titleLayout1.addView(m_titleView1);
        LinearLayout titleLayout = new LinearLayout(m_context);
        titleLayout.setOrientation(LinearLayout.VERTICAL);

        m_titleView = new TextView(m_context);
        m_titleView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        m_titleView.setBackgroundColor(Color.GRAY);
        m_titleView.setTextColor(Color.WHITE);
        m_titleView.setGravity(Gravity.CENTER_VERTICAL);
        m_titleView.setText(title);

        titleLayout.addView(m_titleView);
        input_text = new EditText(m_context);
        input_text.setText(Default_File_Name);
        titleLayout.addView(input_text);

        dialogBuilder.setView(titleLayout);
        dialogBuilder.setCustomTitle(titleLayout1);
        m_listAdapter = createListAdapter(listItems);
        dialogBuilder.setSingleChoiceItems(m_listAdapter, -1, onClickListener);
        dialogBuilder.setCancelable(false);
        return dialogBuilder;
    }

    private void updateDirectory()
    {
        m_subdirs.clear();
        m_subdirs.addAll( getDirectories(m_dir) );
        m_titleView.setText(m_dir);
        m_listAdapter.notifyDataSetChanged();
        input_text.setText(Selected_File_Name);
    }

    private ArrayAdapter<String> createListAdapter(List<String> items)
    {
        return new ArrayAdapter<String>(m_context, android.R.layout.select_dialog_item, android.R.id.text1, items)
        {
            @Override @NonNull
            public View getView(int position, View convertView, @NonNull ViewGroup parent)
            {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView)
                {
                    TextView tv = (TextView) v;
                    tv.getLayoutParams().height = LayoutParams.WRAP_CONTENT;
                    tv.setEllipsize(null);
                }
                return v;
            }
        };
    }
}