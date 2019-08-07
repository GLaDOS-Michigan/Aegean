#!/usr/bin/env python

file_list = ['execs', 'clients', 'filters', 'verifiers']


def get_machines_from_file(file_name):
    ff = open(file_name, 'r')
    _ml = ff.readlines()
    ml = []
    for item in _ml:
        ml = ml + [item.strip()]
    return filter(lambda x: not x.startswith('#'), ml)


def get_machines():
    ml = []
    for f in file_list:
        ml = ml + get_machines_from_file(f)
        ml = ml + get_machines_from_file(f + '.backend')

    ml = set(ml)

    return ml


def getHostname(hostportpair):
    if ':' in hostportpair:
        p = hostportpair.split(':')
        return p[0]
    else:
        return hostportpair
